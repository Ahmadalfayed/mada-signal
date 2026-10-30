package com.watan.mada

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.watan.mada.R

val Teal = Color(0xFF2EC4B6); val Amber = Color(0xFFFFC857); val Navy = Color(0xFF0B1622); val Card = Color(0xFF12212F); val Card2 = Color(0xFF1A2E40)
val Muted = Color(0xFF8FA3B5); val Bad = Color(0xFFE5484D); val Ok = Color(0xFF4CC38A); val Ink = Color(0xFFEAF2F8)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun cairo(w: Int) = Font(R.font.cairo, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Cairo = FontFamily(cairo(400), cairo(600), cairo(700), cairo(800))

fun sigColor(d: Int?): Color = when (quality(d).second) { 4 -> Ok; 3 -> Color(0xFF9BD34C); 2 -> Amber; 1 -> Color(0xFFF08A3C); else -> Bad }
fun ltr(s: String) = "\u200E$s\u200E"

@Composable
fun MadaTheme(content: @Composable () -> Unit) {
    val b = Typography()
    val t = Typography(displayLarge = b.displayLarge.copy(fontFamily = Cairo, fontWeight = FontWeight.ExtraBold), headlineSmall = b.headlineSmall.copy(fontFamily = Cairo, fontWeight = FontWeight.Bold),
        titleLarge = b.titleLarge.copy(fontFamily = Cairo, fontWeight = FontWeight.Bold), titleMedium = b.titleMedium.copy(fontFamily = Cairo, fontWeight = FontWeight.SemiBold),
        titleSmall = b.titleSmall.copy(fontFamily = Cairo), bodyLarge = b.bodyLarge.copy(fontFamily = Cairo), bodyMedium = b.bodyMedium.copy(fontFamily = Cairo),
        bodySmall = b.bodySmall.copy(fontFamily = Cairo), labelLarge = b.labelLarge.copy(fontFamily = Cairo, fontWeight = FontWeight.SemiBold),
        labelMedium = b.labelMedium.copy(fontFamily = Cairo), labelSmall = b.labelSmall.copy(fontFamily = Cairo))
    MaterialTheme(colorScheme = darkColorScheme(primary = Teal, onPrimary = Navy, secondary = Amber, background = Navy, surface = Card, onSurface = Ink,
        onBackground = Ink, surfaceVariant = Card2, onSurfaceVariant = Muted, error = Bad), typography = t) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
    }
}

interface Actions {
    fun toggleLog() {}; fun toggleFinder() {}; fun clearMap() {}; fun resetBest() {}
    fun airplane() {}; fun network() {}; fun prefType() {}; fun radioInfo() {}; fun grant() {}
}

enum class Tab(val label: String, val icon: ImageVector) {
    Live("الإشارة", Icons.Filled.SignalCellularAlt), Finder("الباحث", Icons.Filled.TravelExplore), Map("الخريطة", Icons.Filled.Map), Tools("أدوات", Icons.Filled.Build)
}

@Composable
fun MadaApp(st: AppState, a: Actions, start: Tab = Tab.Live, mapContent: (@Composable (Modifier) -> Unit)? = null) {
    var tab by rememberSaveable { mutableStateOf(start) }
    MadaTheme {
        Scaffold(containerColor = Navy, topBar = { Header(st) }, bottomBar = {
            NavigationBar(containerColor = Card) { Tab.entries.forEach { t -> NavigationBarItem(tab == t, { tab = t }, { Icon(t.icon, null) }, label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Teal.copy(.25f), selectedIconColor = Teal, selectedTextColor = Teal)) } }
        }) { p ->
            Box(Modifier.padding(p).fillMaxSize()) {
                when (tab) { Tab.Live -> Live(st, a); Tab.Finder -> Finder(st, a); Tab.Map -> MapTab(st, a, mapContent); Tab.Tools -> Tools(st, a) }
            }
        }
    }
}

@Composable fun Header(st: AppState) {
    Row(Modifier.fillMaxWidth().background(Navy).statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(androidx.compose.ui.res.painterResource(R.drawable.ic_logo), null, Modifier.size(42.dp).clip(CircleShape).background(Card2))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text("مدى", style = MaterialTheme.typography.titleLarge); Text("الباحث عن أقوى إشارة", style = MaterialTheme.typography.bodySmall, color = Muted) }
        val r = st.now.value
        Column(horizontalAlignment = Alignment.End) { Text(r?.operator ?: "—", style = MaterialTheme.typography.titleSmall, color = Amber); Text(r?.type ?: "", style = MaterialTheme.typography.labelMedium, color = Muted) }
    }
}

@Composable fun Box2(m: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(m.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp), content = content)

@Composable fun PermBanner(st: AppState, a: Actions) { if (!st.permsOk.value) Box2(Modifier.padding(bottom = 12.dp)) {
    Text("يحتاج «مدى» إذن الموقع والهاتف عشان يقرأ الإشارة ويرسم الخريطة.", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp)); Button(a::grant) { Text("السماح") } } }

@Composable fun Live(st: AppState, a: Actions) {
    val r = st.now.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PermBanner(st, a)
        Box2 { Gauge(r?.dbm, Modifier.fillMaxWidth().height(210.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("الجودة", quality(r?.dbm).first, sigColor(r?.dbm)); Stat("الشبكة", r?.type ?: "—"); Bars(quality(r?.dbm).second) } }
        Spacer(Modifier.height(12.dp))
        Box2 { Text("آخر دقيقتين", style = MaterialTheme.typography.titleSmall, color = Muted); Spacer(Modifier.height(6.dp)); Graph(st.history, Modifier.fillMaxWidth().height(120.dp)) }
        Spacer(Modifier.height(12.dp))
        Box2 { Text("تفاصيل الإشارة", style = MaterialTheme.typography.titleMedium)
            Grid(listOf("RSRP" to r?.rsrp?.let { "$it dBm" }, "RSRQ" to r?.rsrq?.let { "$it dB" }, "SINR" to r?.sinr?.let { "$it dB" }, "أفضل قراءة" to st.best.value?.let { "$it dBm" })) }
        Spacer(Modifier.height(12.dp))
        Box2 { Text("البرج المتصل فيه", style = MaterialTheme.typography.titleMedium); val c = r?.cell
            Grid(listOf("المشغّل" to r?.operator, "النطاق" to c?.band, "رقم الخلية CI" to c?.cid?.toString(), "المنطقة TAC" to c?.area?.toString(),
                "PCI" to c?.pci?.toString(), "MCC-MNC" to c?.let { "${it.mcc}-${it.mnc}" }))
            Text("مكان البرج نفسه ما يعطيه الجوال، وقواعد بيانات الأبراج تحتاج مفاتيح خاصة. شوف «الخريطة» لاتجاه الإشارة الأقوى حسب قراءاتك.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable fun Stat(l: String, v: String, c: Color = Ink) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(l, style = MaterialTheme.typography.labelMedium, color = Muted); Text(v, style = MaterialTheme.typography.titleMedium, color = c) }

@Composable fun Bars(n: Int) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("الأعمدة", style = MaterialTheme.typography.labelMedium, color = Muted)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.height(26.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..4) Box(Modifier.width(7.dp).height((6 * i).dp).clip(RoundedCornerShape(2.dp)).background(if (i <= n) sigColor(-80 - (4 - n) * 10) else Card2)) } } }

@Composable fun Grid(items: List<Pair<String, String?>>) = Column { items.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
    row.forEach { (k, v) -> Column(Modifier.weight(1f)) { Text(k, style = MaterialTheme.typography.labelMedium, color = Muted); Text(ltr(v ?: "—"), style = MaterialTheme.typography.titleMedium) } } } } }

@Composable fun Gauge(dbm: Int?, m: Modifier) = Box(m, contentAlignment = Alignment.Center) {
    val frac = ((dbm ?: -140) + 140) / 90f
    val col = sigColor(dbm)
    Canvas(Modifier.fillMaxSize()) {
        val s = minOf(size.width, size.height * 1.6f) * .8f; val tl = Offset((size.width - s) / 2, 16f)
        drawArc(Card2, 150f, 240f, false, tl, Size(s, s), style = Stroke(36f, cap = StrokeCap.Round))
        drawArc(Brush.sweepGradient(listOf(Bad, Amber, Ok, Bad)), 150f, 240f * frac.coerceIn(0.02f, 1f), false, tl, Size(s, s), style = Stroke(36f, cap = StrokeCap.Round))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ltr(dbm?.toString() ?: "--"), style = MaterialTheme.typography.displayLarge, color = col)
        Text("dBm", style = MaterialTheme.typography.titleMedium, color = Muted) }
}

@Composable fun Graph(h: List<Int>, m: Modifier) = Canvas(m.clip(RoundedCornerShape(12.dp)).background(Card2)) {
    for (lv in listOf(-85, -95, -105, -115)) { val y = size.height * (-60 - lv) / 80f; drawLine(Color.White.copy(.08f), Offset(0f, y), Offset(size.width, y)) }
    if (h.size < 2) return@Canvas
    val step = size.width / 119f; val pts = h.mapIndexed { i, v -> Offset(size.width - (h.size - 1 - i) * step, size.height * (-60 - v).coerceIn(0, 80) / 80f) }
    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
    drawPath(path, Teal, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(sigColor(h.last()), 9f, pts.last())
}

@Composable fun Finder(st: AppState, a: Actions) {
    val h = st.history; val now = h.lastOrNull()
    val prev = if (h.size > 6) h.takeLast(7).dropLast(3).average() else null
    val trend = if (now == null || prev == null) 0 else (now - prev).let { if (it >= 2) 1 else if (it <= -2) -1 else 0 }
    val (msg, col) = when { !st.finder.value -> "اضغط «ابدأ» وامشِ ببطء" to Muted; trend > 0 -> "دافي 🔥 كمّل بنفس الاتجاه" to Ok; trend < 0 -> "بارد ❄️ ارجع أو غيّر الاتجاه" to Teal; else -> "ثابت… جرّب اتجاه ثاني" to Amber }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PermBanner(st, a)
        Box(Modifier.size(240.dp).clip(CircleShape).background(Brush.radialGradient(listOf(col.copy(.45f), Navy))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(ltr(now?.toString() ?: "--"), style = MaterialTheme.typography.displayLarge, color = sigColor(now)); Text("dBm", color = Muted) } }
        Spacer(Modifier.height(14.dp)); Text(msg, style = MaterialTheme.typography.headlineSmall, color = col, textAlign = TextAlign.Center)
        Row { Text("أفضل قراءة: ", color = Muted); CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(st.best.value?.let { "$it dBm" } ?: "—", color = Muted, style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)) } }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(a::toggleFinder, Modifier.height(52.dp)) { Icon(if (st.finder.value) Icons.Filled.Stop else Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (st.finder.value) "إيقاف" else "ابدأ البحث") }
            OutlinedButton(a::resetBest, Modifier.height(52.dp)) { Text("تصفير الأفضل") } }
        Spacer(Modifier.height(16.dp))
        Box2 { Text("كيف يشتغل؟", style = MaterialTheme.typography.titleMedium)
            Text("كل ما قويت الإشارة يسرع الصفير ويهتز الجوال اهتزازة خفيفة. امشِ ببطء في البيت، وقف عند الشبابيك والأماكن العالية، وخلّ الجوال بيدك بنفس الطريقة.", style = MaterialTheme.typography.bodyMedium, color = Muted) }
    }
}

@Composable fun MapTab(st: AppState, a: Actions, mapContent: (@Composable (Modifier) -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PermBanner(st, a)
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Card2)) {
            mapContent?.invoke(Modifier.fillMaxSize()) ?: HeatPreview(st.points, Modifier.fillMaxSize())
            val dir = strongerDirection(st.lat.value, st.lon.value, st.points)
            if (dir != null) Row(Modifier.align(Alignment.TopCenter).padding(10.dp).clip(RoundedCornerShape(50)).background(Navy.copy(.85f)).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Navigation, null, Modifier.rotate(dir.first.toFloat()), tint = Ok); Spacer(Modifier.width(6.dp))
                Text("الإشارة أقوى باتجاه ${compass(dir.first)} (~${ltr("${dir.second} م")})", style = MaterialTheme.typography.labelLarge) }
            Legend(Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(a::toggleLog, Modifier.weight(1f)) { Icon(if (st.logging.value) Icons.Filled.Stop else Icons.Filled.FiberManualRecord, null); Spacer(Modifier.width(6.dp)); Text(if (st.logging.value) "إيقاف التسجيل" else "سجّل وأنا أمشي") }
            OutlinedButton(a::clearMap) { Text("مسح") } }
        Text("${st.points.size} نقطة • الخريطة تنحفظ في الجهاز بعد ما تشوفها مرة، فتشتغل بالبر بدون نت.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

fun compass(b: Double) = listOf("الشمال", "الشمال الشرقي", "الشرق", "الجنوب الشرقي", "الجنوب", "الجنوب الغربي", "الغرب", "الشمال الغربي")[(((b + 22.5) % 360) / 45).toInt()]

@Composable fun Legend(m: Modifier) = Row(m.clip(RoundedCornerShape(12.dp)).background(Navy.copy(.85f)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    listOf(-80 to "ممتازة", -100 to "متوسطة", -120 to "ضعيفة").forEach { (d, l) -> Box(Modifier.size(10.dp).clip(CircleShape).background(sigColor(d))); Text(l, style = MaterialTheme.typography.labelSmall) } }

@Composable fun HeatPreview(pts: List<Reading>, m: Modifier) = Canvas(m.background(Color(0xFF1F3446))) {
    for (i in 0..10) { drawLine(Color.White.copy(.05f), Offset(0f, size.height * i / 10), Offset(size.width, size.height * i / 10)); drawLine(Color.White.copy(.05f), Offset(size.width * i / 10, 0f), Offset(size.width * i / 10, size.height)) }
    val g = pts.filter { it.lat != null }; if (g.isEmpty()) return@Canvas
    val minY = g.minOf { it.lat!! }; val maxY = g.maxOf { it.lat!! }; val minX = g.minOf { it.lon!! }; val maxX = g.maxOf { it.lon!! }
    g.forEach { p -> val x = 40 + (size.width - 80) * ((p.lon!! - minX) / (maxX - minX + 1e-9)).toFloat(); val y = 40 + (size.height - 80) * (1 - ((p.lat!! - minY) / (maxY - minY + 1e-9)).toFloat())
        drawCircle(sigColor(p.dbm).copy(.35f), 34f, Offset(x, y)); drawCircle(sigColor(p.dbm), 10f, Offset(x, y)) }
}

@Composable fun Tools(st: AppState, a: Actions) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Box2 { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Info, null, tint = Amber); Spacer(Modifier.width(8.dp)); Text("بصراحة", style = MaterialTheme.typography.titleMedium, color = Amber) }
        Text("ما فيه تطبيق يقدر يقوّي إشارة الجوال. «مدى» يساعدك تلقى المكان الأقوى، ويعيد اتصالك بالبرج. للتقوية الحقيقية تحتاج جهاز مقوّي معتمد أو راوتر بهوائي خارجي.", style = MaterialTheme.typography.bodyMedium) }
    Spacer(Modifier.height(12.dp))
    Text("إجراءات سريعة", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
    Action(Icons.Filled.AirplanemodeActive, "وضع الطيران", "شغّله 10 ثواني وطفّه، يعيد ربط الجوال بأقرب برج", a::airplane)
    Action(Icons.Filled.CellTower, "إعدادات شبكة الجوال", "اختيار المشغّل يدوياً أو تلقائياً", a::network)
    Action(Icons.Filled.NetworkCell, "نوع الشبكة المفضّل", "إذا الـ 5G ضعيف عندك جرّب 4G بس", a::prefType)
    Action(Icons.Filled.Tune, "معلومات الراديو المتقدمة", "شاشة مخفية في بعض الأجهزة (*#*#4636#*#*)", a::radioInfo)
    Spacer(Modifier.height(12.dp))
    Box2 { Text("نصايح لإشارة أقوى", style = MaterialTheme.typography.titleMedium)
        listOf("قرّب من الشبابيك، والزجاج العاكس يضعّف الإشارة.", "الأدوار العالية والسطح غالباً أقوى.", "شيل الكفر المعدني أو المغناطيسي.", "في البر، اطلع على مكان مرتفع ووجّه نفسك صوب اتجاه الإشارة الأقوى بالخريطة.",
            "فعّل الاتصال عبر الواي فاي (VoWiFi) إذا عندك نت بالبيت.", "حدّث نظام الجوال، وجرّب تطلع الشريحة وترجعها.").forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 6.dp)) } }
    Spacer(Modifier.height(12.dp))
    Text("مدى 1.0 • جميع الحقوق محفوظة © 2026 وطن النهار", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

@Composable fun Action(i: ImageVector, t: String, s: String, on: () -> Unit) = Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(Card).clickable(onClick = on).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Teal.copy(.18f)), contentAlignment = Alignment.Center) { Icon(i, null, tint = Teal) }
    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t, style = MaterialTheme.typography.titleSmall); Text(s, style = MaterialTheme.typography.bodySmall, color = Muted) }
    Icon(Icons.Filled.ChevronLeft, null, tint = Muted)
}
