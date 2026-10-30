package com.watan.mada

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.telephony.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

data class Cell(val mcc: String? = null, val mnc: String? = null, val area: Int? = null, val cid: Long? = null, val pci: Int? = null,
                val channel: Int? = null, val band: String? = null)

data class Reading(val dbm: Int?, val level: Int, val type: String, val operator: String, val rsrp: Int? = null, val rsrq: Int? = null,
                   val sinr: Int? = null, val cell: Cell? = null, val lat: Double? = null, val lon: Double? = null, val t: Long = System.currentTimeMillis())

class AppState {
    val now = mutableStateOf<Reading?>(null)
    val history = mutableStateListOf<Int>()           // recent dBm values for the live graph
    val points = mutableStateListOf<Reading>()        // logged map points
    val logging = mutableStateOf(false)
    val finder = mutableStateOf(false)
    val best = mutableStateOf<Int?>(null)
    val lat = mutableStateOf<Double?>(null); val lon = mutableStateOf<Double?>(null)
    val permsOk = mutableStateOf(true)
}

/** Kuwait MCC 419 operators. */
fun operatorName(mcc: String?, mnc: String?, fallback: String?): String = when ("$mcc-$mnc") {
    "419-02" -> "زين Zain"; "419-03" -> "أوريدو Ooredoo"; "419-04" -> "stc الكويت"
    else -> fallback?.takeIf { it.isNotBlank() } ?: "—"
}

fun quality(dbm: Int?): Pair<String, Int> = when {
    dbm == null -> "لا توجد قراءة" to 0
    dbm >= -85 -> "ممتازة" to 4; dbm >= -95 -> "جيدة" to 3; dbm >= -105 -> "متوسطة" to 2; dbm >= -115 -> "ضعيفة" to 1; else -> "ضعيفة جداً" to 0
}

/** LTE band from EARFCN (bands used in Kuwait). */
fun lteBand(e: Int?): String? = when (e) {
    null -> null; in 0..599 -> "B1 2100"; in 1200..1949 -> "B3 1800"; in 2750..3449 -> "B7 2600"; in 3450..3799 -> "B8 900"
    in 6150..6449 -> "B20 800"; in 37750..38249 -> "B38 2600"; in 38650..39649 -> "B40 2300"; in 41590..43589 -> "B41 2500"; else -> "EARFCN $e"
}
fun nrBand(a: Int?): String? = when (a) { null -> null; in 620000..653333 -> "n78 3500"; in 422000..434000 -> "n1 2100"; in 361000..376000 -> "n3 1800"; else -> "NR-ARFCN $a" }

/** Bearing (deg) from (lat,lon) toward the signal-weighted centroid of the strongest logged points. Honest: based on user's own readings only. */
fun strongerDirection(lat: Double?, lon: Double?, pts: List<Reading>): Pair<Double, Int>? {
    if (lat == null || lon == null) return null
    val good = pts.filter { it.lat != null && it.dbm != null }.sortedByDescending { it.dbm }.take(max(3, pts.size / 5))
    if (good.size < 3) return null
    var wx = 0.0; var wy = 0.0; var w = 0.0
    for (p in good) { val k = (p.dbm!! + 140).toDouble().coerceAtLeast(1.0); wx += p.lon!! * k; wy += p.lat!! * k; w += k }
    val tLat = wy / w; val tLon = wx / w
    val dLon = Math.toRadians(tLon - lon); val y = sin(dLon) * cos(Math.toRadians(tLat))
    val x = cos(Math.toRadians(lat)) * sin(Math.toRadians(tLat)) - sin(Math.toRadians(lat)) * cos(Math.toRadians(tLat)) * cos(dLon)
    val brg = (Math.toDegrees(atan2(y, x)) + 360) % 360
    val dist = FloatArray(1); Location.distanceBetween(lat, lon, tLat, tLon, dist)
    return brg to dist[0].roundToInt()
}

class SignalMonitor(private val ctx: Context, private val st: AppState) {
    private val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val store = File(ctx.filesDir, "points.json")
    private val listener = LocationListener { l: Location -> st.lat.value = l.latitude; st.lon.value = l.longitude }

    init { load() }

    @SuppressLint("MissingPermission")
    fun startLocation() = try {
        (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))?.let(listener::onLocationChanged)
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER))
            if (lm.allProviders.contains(p)) lm.requestLocationUpdates(p, 2000L, 2f, listener, Looper.getMainLooper())
        Unit
    } catch (_: Exception) {}

    @SuppressLint("MissingPermission")
    fun sample() {
        var dbm: Int? = null; var level = 0; var rsrp: Int? = null; var rsrq: Int? = null; var sinr: Int? = null; var cell: Cell? = null
        val ok = { v: Int -> v != CellInfo.UNAVAILABLE && v != Int.MAX_VALUE }
        try {
            val reg = tm.allCellInfo?.firstOrNull { it.isRegistered }
            when (reg) {
                is CellInfoLte -> { val s = reg.cellSignalStrength; val i = reg.cellIdentity
                    rsrp = s.rsrp.takeIf(ok); rsrq = s.rsrq.takeIf(ok); sinr = s.rssnr.takeIf(ok)
                    cell = Cell(i.mccString, i.mncString, i.tac.takeIf(ok), i.ci.takeIf(ok)?.toLong(), i.pci.takeIf(ok), i.earfcn.takeIf(ok), lteBand(i.earfcn.takeIf(ok))) }
                is CellInfoNr -> { val s = reg.cellSignalStrength as CellSignalStrengthNr; val i = reg.cellIdentity as CellIdentityNr
                    rsrp = s.ssRsrp.takeIf(ok); rsrq = s.ssRsrq.takeIf(ok); sinr = s.ssSinr.takeIf(ok)
                    cell = Cell(i.mccString, i.mncString, i.tac.takeIf(ok), i.nci.takeIf { it != Long.MAX_VALUE }, i.pci.takeIf(ok), i.nrarfcn.takeIf(ok), nrBand(i.nrarfcn.takeIf(ok))) }
                is CellInfoWcdma -> { val i = reg.cellIdentity; cell = Cell(i.mccString, i.mncString, i.lac.takeIf(ok), i.cid.takeIf(ok)?.toLong(), i.psc.takeIf(ok), i.uarfcn.takeIf(ok), "3G") }
                is CellInfoGsm -> { val i = reg.cellIdentity; cell = Cell(i.mccString, i.mncString, i.lac.takeIf(ok), i.cid.takeIf(ok)?.toLong(), null, i.arfcn.takeIf(ok), "2G") }
                else -> {}
            }
            val ss = tm.signalStrength
            val c = ss?.cellSignalStrengths?.firstOrNull()
            level = c?.level ?: ss?.level ?: 0
            dbm = (rsrp ?: c?.dbm)?.takeIf { it in -150..-20 }
        } catch (_: Exception) {}
        val type = try { netName(tm.dataNetworkType) } catch (_: SecurityException) { "؟" }
        val nop = try { tm.networkOperator } catch (_: Exception) { "" }
        val op = operatorName(cell?.mcc ?: nop.take(3).ifBlank { null }, cell?.mnc ?: nop.drop(3).ifBlank { null }, tm.networkOperatorName)
        val r = Reading(dbm, level, type, op, rsrp, rsrq, sinr, cell, st.lat.value, st.lon.value)
        st.now.value = r
        dbm?.let { st.history.add(it); if (st.history.size > 120) st.history.removeAt(0); if (st.best.value == null || it > st.best.value!!) st.best.value = it }
        if (st.logging.value && r.lat != null && dbm != null) { st.points.add(r); if (st.points.size % 5 == 0) save() }
    }

    fun save() = try {
        val a = JSONArray(); st.points.takeLast(5000).forEach { a.put(JSONObject().put("d", it.dbm).put("y", it.lat).put("x", it.lon).put("n", it.type).put("t", it.t)) }
        store.writeText(a.toString())
    } catch (_: Exception) {}
    private fun load() { try {
        if (store.exists()) { val a = JSONArray(store.readText()); for (i in 0 until a.length()) { val o = a.getJSONObject(i)
            st.points.add(Reading(o.getInt("d"), 0, o.optString("n"), "", lat = o.getDouble("y"), lon = o.getDouble("x"), t = o.getLong("t"))) } }
    } catch (_: Exception) {} }
    fun clear() { st.points.clear(); store.delete() }

    private fun netName(t: Int) = when (t) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"; TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "لا شبكة"; else -> "أخرى"
    }
}
