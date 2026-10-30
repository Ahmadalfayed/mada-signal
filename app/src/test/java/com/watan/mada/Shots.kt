package com.watan.mada

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-w393dp-h852dp-xxhdpi")
class Shots {
    @get:Rule val rule = createComposeRule()
    private fun sample() = AppState().apply {
        now.value = Reading(-97, 3, "4G", "زين Zain", -97, -11, 9, Cell("419", "02", 3021, 12345678, 241, 1850, lteBand(1850)), 29.3759, 47.9774)
        for (i in 0 until 120) history.add((-104 + 8 * sin(i / 9.0) + i / 20.0).toInt())
        best.value = -88; lat.value = 29.3759; lon.value = 47.9774
        for (i in 0 until 60) { val y = 29.3740 + (i % 10) * 0.0004; val x = 47.9760 + (i / 10) * 0.0005
            points.add(Reading((-120 + (i % 10) * 3 + (i / 10) * 2), 0, "4G", "", lat = y, lon = x)) }
    }
    private fun shot(tab: Tab, name: String, st: AppState = sample()) {
        rule.setContent { MadaApp(st, object : Actions {}, tab) }; rule.onRoot().captureRoboImage("../screenshots/$name.png") }
    @Test fun live() = shot(Tab.Live, "01-live")
    @Test fun finder() = shot(Tab.Finder, "02-finder", sample().apply { finder.value = true; history.addAll(listOf(-100, -99, -97, -95, -93, -91, -90)) })
    @Test fun map() = shot(Tab.Map, "03-map")
    @Test fun tools() = shot(Tab.Tools, "04-tools")
    @Test fun noPerm() = shot(Tab.Live, "05-no-permission", AppState().apply { permsOk.value = false })
}
