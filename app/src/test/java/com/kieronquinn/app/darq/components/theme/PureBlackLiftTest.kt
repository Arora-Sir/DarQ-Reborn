package com.kieronquinn.app.darq.components.theme

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.MaterialColors
import com.kieronquinn.app.darq.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "night")
class PureBlackLiftTest {

    @Test
    fun storedValuesSnapToTenSteps() {
        assertEquals(0, PureBlackLift.stepOf(0))
        assertEquals(5, PureBlackLift.stepOf(50))
        assertEquals(10, PureBlackLift.stepOf(100))
        assertEquals(5, PureBlackLift.stepOf(54))
        assertEquals(6, PureBlackLift.stepOf(55))
        assertEquals(0, PureBlackLift.stepOf(-30))
        assertEquals(10, PureBlackLift.stepOf(999))
    }

    @Test
    fun recommendedLevelIsTheBlueprintLook() {
        val ladder = PureBlackLift.ladder(PureBlackLift.DEFAULT)
        assertEquals(13, ladder.container)
        assertEquals(22, ladder.high)
        assertEquals(31, ladder.highest)
    }

    // NOTE: Both ends must look different from the recommended level. A flat end only 3 grey levels away made half the DualClone slider look identical.
    @Test
    fun bothEndsAreFarEnoughFromTheRecommendedLevel() {
        val flat = PureBlackLift.ladder(0)
        val recommended = PureBlackLift.ladder(50)
        val lifted = PureBlackLift.ladder(100)
        assertTrue(recommended.container - flat.container >= 8)
        assertTrue(recommended.high - flat.high >= 12)
        assertTrue(lifted.container - recommended.container >= 12)
        assertTrue(lifted.high - recommended.high >= 18)
    }

    @Test
    fun everyStepRaisesEverySurfaceAndKeepsTheOrder() {
        var previous = PureBlackLift.ladder(0)
        for (step in 1..PureBlackLift.STEPS) {
            val current = PureBlackLift.ladder(step * 10)
            assertTrue(current.container > previous.container)
            assertTrue(current.high > previous.high)
            assertTrue(current.highest > previous.highest)
            assertTrue(current.containerLow < current.container && current.container < current.high && current.high < current.highest)
            previous = current
        }
    }

    @Test
    fun generatedOverlaysMatchTheLadder() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        for (step in 0..PureBlackLift.STEPS) {
            val themed = ContextThemeWrapper(base, R.style.AppTheme)
            themed.theme.applyStyle(PureBlackLift.overlayFor(step * 10), true)
            val ladder = PureBlackLift.ladder(step * 10)
            assertEquals(Color.BLACK, color(themed, com.google.android.material.R.attr.colorSurface))
            assertEquals(grey(ladder.containerLow), color(themed, com.google.android.material.R.attr.colorSurfaceContainerLow))
            assertEquals(grey(ladder.container), color(themed, com.google.android.material.R.attr.colorSurfaceContainer))
            assertEquals(grey(ladder.high), color(themed, com.google.android.material.R.attr.colorSurfaceContainerHigh))
            assertEquals(grey(ladder.highest), color(themed, com.google.android.material.R.attr.colorSurfaceContainerHighest))
        }
    }

    @Test
    fun theDefaultOverlayIsTheRecommendedLevel() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val themed = ContextThemeWrapper(base, R.style.AppTheme)
        themed.theme.applyStyle(R.style.ThemeOverlay_Darq_PureBlack, true)
        assertEquals(grey(13), color(themed, com.google.android.material.R.attr.colorSurfaceContainer))
    }

    private fun color(context: Context, attr: Int) = MaterialColors.getColor(context, attr, Color.TRANSPARENT)

    private fun grey(level: Int) = Color.rgb(level, level, level)

}
