package com.kieronquinn.app.darq.utils.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressFeedbackTest {

    @Test
    fun smallTilesKeepThePercentageShrink() {
        // A 48 dp swatch on a 3x screen is 144 px wide, 10 dp is 30 px which is 21 percent, so the base 0.97 wins
        assertEquals(0.97f, pressedScaleFor(144f, 3f), 0.0001f)
    }

    @Test
    fun phoneRowsShrinkAboutThreePercent() {
        // A 1080 px row at 2.8x, 10 dp is 28 px, 2.6 percent, which is gentler than the base so the cap applies
        val scale = pressedScaleFor(1080f, 2.8f)
        assertTrue(scale in 0.97f..0.98f)
    }

    @Test
    fun wideTabletRowsBarelyMove() {
        // A 1950 px row at 2x moves by 10 dp, 20 px in total, not 58 px
        val shrinkPx = (1f - pressedScaleFor(1950f, 2f)) * 1950f
        assertTrue(shrinkPx <= 21f)
    }

    @Test
    fun theShrinkIsNeverBelowTheGentlestScale() {
        assertEquals(0.995f, pressedScaleFor(100000f, 2f), 0.0001f)
    }

}
