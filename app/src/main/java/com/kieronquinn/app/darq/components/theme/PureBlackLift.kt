package com.kieronquinn.app.darq.components.theme

import androidx.annotation.StyleRes
import com.kieronquinn.app.darq.R

// NOTE: The Pure black surface lift moves cards and fields between a flat look and a lifted look while the page stays #000000.
// NOTE: The stored value is 0 to 100 and the control snaps to ten steps, so a finer scale can come later without a migration.
// NOTE: The ladder is the one from the blueprint (DualClone), and scripts/theme/generate_pure_black.py writes the same greys into the overlays.
object PureBlackLift {

    const val DEFAULT = 50
    const val STEPS = 10

    data class Ladder(val containerLow: Int, val container: Int, val high: Int, val highest: Int)

    private val CONTAINER = intArrayOf(4, 13, 27)
    private val HIGH = intArrayOf(8, 22, 42)
    private val HIGHEST = intArrayOf(12, 31, 52)

    private val overlays = intArrayOf(
        R.style.ThemeOverlay_Darq_PureBlack_Lift0, R.style.ThemeOverlay_Darq_PureBlack_Lift1,
        R.style.ThemeOverlay_Darq_PureBlack_Lift2, R.style.ThemeOverlay_Darq_PureBlack_Lift3,
        R.style.ThemeOverlay_Darq_PureBlack_Lift4, R.style.ThemeOverlay_Darq_PureBlack_Lift5,
        R.style.ThemeOverlay_Darq_PureBlack_Lift6, R.style.ThemeOverlay_Darq_PureBlack_Lift7,
        R.style.ThemeOverlay_Darq_PureBlack_Lift8, R.style.ThemeOverlay_Darq_PureBlack_Lift9,
        R.style.ThemeOverlay_Darq_PureBlack_Lift10
    )

    /** The step from 0 to 10 that a stored value snaps to. */
    fun stepOf(lift: Int) = ((lift.coerceIn(0, 100) + 5) / 10).coerceIn(0, STEPS)

    @StyleRes
    fun overlayFor(lift: Int): Int = overlays[stepOf(lift)]

    /** Grey levels, 0 to 255, of the surface roles for a stored value. Mirrors the generated overlays. */
    fun ladder(lift: Int): Ladder {
        val value = stepOf(lift) * 10
        fun interpolate(anchors: IntArray): Int {
            val result = if (value <= 50) anchors[0] + (anchors[1] - anchors[0]) * value / 50f
            else anchors[1] + (anchors[2] - anchors[1]) * (value - 50) / 50f
            return (result + 0.5f).toInt().coerceIn(0, 255)
        }
        val container = interpolate(CONTAINER)
        return Ladder((container / 2f + 0.5f).toInt(), container, interpolate(HIGH), interpolate(HIGHEST))
    }

}
