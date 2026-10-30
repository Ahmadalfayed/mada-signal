package com.watan.mada

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

class MainActivity : ComponentActivity(), Actions {
    private val st = AppState()
    private lateinit var mon: SignalMonitor
    private var tone: ToneGenerator? = null
    private val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Configuration.getInstance().apply { userAgentValue = "MadaSignal/1.0 (com.watan.mada)"; tileFileSystemCacheMaxBytes = 400L * 1024 * 1024 }
        mon = SignalMonitor(this, st)
        checkPerms()
        setContent { MadaApp(st, this, mapContent = { m -> HeatMap(m) }) }
        lifecycleScope.launch { var tick = 0L
            while (true) { mon.sample(); if (st.finder.value) guide(tick); tick++; delay(if (st.finder.value) 500 else 1000) } }
    }

    private fun checkPerms() {
        st.permsOk.value = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (st.permsOk.value) mon.startLocation()
    }
    override fun grant() = requestPermissions(perms, 1)
    override fun onRequestPermissionsResult(c: Int, p: Array<String>, r: IntArray) { super.onRequestPermissionsResult(c, p, r); checkPerms() }
    override fun onResume() { super.onResume(); if (::mon.isInitialized && !st.permsOk.value && perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) checkPerms() }
    override fun onPause() { super.onPause(); mon.save() }

    /** Beep rate & vibration scale with signal: stronger = faster. */
    private fun guide(tick: Long) {
        val d = st.now.value?.dbm ?: return
        val period = when (quality(d).second) { 4 -> 1; 3 -> 2; 2 -> 3; 1 -> 4; else -> 6 }
        if (tick % period != 0L) return
        if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        val best = st.best.value
        if (best != null && d >= best) vibrate(60)
    }
    private fun vibrate(ms: Long) = try {
        val v = if (Build.VERSION.SDK_INT >= 31) (getSystemService(VibratorManager::class.java)).defaultVibrator else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}

    override fun toggleLog() { if (!st.permsOk.value) return grant(); st.logging.value = !st.logging.value; if (!st.logging.value) mon.save() }
    override fun toggleFinder() { if (!st.permsOk.value) return grant(); st.finder.value = !st.finder.value }
    override fun resetBest() { st.best.value = null }
    override fun clearMap() = mon.clear()
    private fun open(vararg actions: String) { for (a in actions) try { startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        Toast.makeText(this, "ما قدرت أفتحها في هالجهاز، افتحها من الإعدادات", Toast.LENGTH_LONG).show() }
    override fun airplane() = open(Settings.ACTION_AIRPLANE_MODE_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
    override fun network() = open(Settings.ACTION_NETWORK_OPERATOR_SETTINGS, Settings.ACTION_DATA_ROAMING_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
    override fun prefType() = open(Settings.ACTION_DATA_ROAMING_SETTINGS, Settings.ACTION_NETWORK_OPERATOR_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
    override fun radioInfo() { try { startActivity(Intent().setClassName("com.android.phone", "com.android.phone.settings.RadioInfo")) }
        catch (_: Exception) { try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:*#*#4636#*#*"))) } catch (_: Exception) {} } }

    @Composable private fun HeatMap(m: Modifier) {
        val n = st.points.size; val lat = st.lat.value; val lon = st.lon.value
        var centered by remember { mutableStateOf(false) }
        AndroidView({ c -> MapView(c).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(16.0)
            controller.setCenter(GeoPoint(29.3759, 47.9774)) } }, m) { mv ->
            if (!centered && lat != null && lon != null) { mv.controller.setCenter(GeoPoint(lat, lon)); centered = true }
            if (mv.overlays.size != n) { mv.overlays.clear()
                st.points.forEach { p -> mv.overlays.add(Polygon(mv).apply { points = Polygon.pointsAsCircle(GeoPoint(p.lat!!, p.lon!!), 6.0)
                    val c = sigColor(p.dbm); val argb = android.graphics.Color.argb(150, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                    fillPaint.color = argb; outlinePaint.color = argb; outlinePaint.strokeWidth = 0f; outlinePaint.style = Paint.Style.STROKE; setOnClickListener { _, _, _ -> true } }) }
                mv.invalidate() }
        }
    }
}
