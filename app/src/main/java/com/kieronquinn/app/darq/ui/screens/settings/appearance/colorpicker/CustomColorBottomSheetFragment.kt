package com.kieronquinn.app.darq.ui.screens.settings.appearance.colorpicker

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.components.settings.DarqSharedPreferences
import com.kieronquinn.app.darq.components.theme.ThemeManager
import com.kieronquinn.app.darq.databinding.FragmentBottomSheetCustomColorBinding
import com.kieronquinn.app.darq.ui.base.BaseBottomSheetFragment
import com.kieronquinn.app.darq.utils.extensions.applyPressFeedback
import org.koin.android.ext.android.inject

// NOTE: The picked color is kept in the saved state, so a rotation or recreate() does not lose it.
// NOTE: Android 12 and later grow a full Material scheme from the color. Android 10 and 11 use the closest preset, and the sheet says so.
class CustomColorBottomSheetFragment : BaseBottomSheetFragment<FragmentBottomSheetCustomColorBinding>(FragmentBottomSheetCustomColorBinding::inflate) {

    companion object {
        private const val STATE_COLOR = "color"
        private const val PALETTE_SPAN = 6
        private const val MAX_COLUMN_DP = 400
        private const val LOW_CONTRAST = 3.0
        private const val CHROME_DP = 200
        private const val MIN_SCROLL_DP = 120
        private const val UNSET_COLOR = Integer.MAX_VALUE
    }

    private val settings by inject<DarqSharedPreferences>()

    private var color = Color.BLUE
    private var updating = false
    private var paletteAdapter: PaletteAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        color = savedInstanceState?.getInt(STATE_COLOR) ?: initialColor()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_COLOR, color)
    }

    private fun initialColor(): Int {
        val stored = settings.monetColor
        return if (settings.themeAccent == ThemeManager.ACCENT_CUSTOM && stored != UNSET_COLOR) stored or 0xFF000000.toInt()
        else themeColors.getAccentColor(requireContext()) or 0xFF000000.toInt()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        with(binding) {
            // NOTE: With the keyboard open the sheet is lifted by its height, so the scroll area is shortened to what is left above it.
            // NOTE: The chrome estimate covers the title, the button row and the status bar.
            val density = resources.displayMetrics.density
            val screenHeight = resources.displayMetrics.heightPixels
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val bottom = maxOf(navigation.bottom, ime.bottom)
                v.updatePadding(left = navigation.left, right = navigation.right, bottom = bottom + resources.getDimension(R.dimen.padding_8).toInt())
                val chrome = (CHROME_DP * density).toInt()
                val available = screenHeight - statusBar - bottom - chrome
                val maxHeight = available.coerceIn((MIN_SCROLL_DP * density).toInt(), (screenHeight * 0.75f).toInt())
                (customColorScroll.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).matchConstraintMaxHeight = maxHeight
                customColorScroll.requestLayout()
                insets
            }
            customColorHex.setOnFocusChangeListener { _, focused ->
                if (focused) customColorScroll.postDelayed({ customColorScroll.smoothScrollTo(0, customColorColumn.height) }, 250)
            }
            val maxColumn = (MAX_COLUMN_DP * resources.displayMetrics.density).toInt()
            customColorColumn.updateLayoutParams<ViewGroup.LayoutParams> { width = ViewGroup.LayoutParams.MATCH_PARENT }
            customColorColumn.post {
                if (customColorColumn.width > maxColumn) customColorColumn.updateLayoutParams<ViewGroup.LayoutParams> { width = maxColumn }
            }

            customColorPreviewSurface.background = rounded(themeColors.getBackgroundColor(context), context)
            customColorPreviewBlack.background = rounded(Color.BLACK, context)

            paletteAdapter = PaletteAdapter { picked -> setColor(picked) }
            customColorPalette.layoutManager = GridLayoutManager(context, PALETTE_SPAN)
            customColorPalette.adapter = paletteAdapter

            customColorArea.onChanged = { s, v -> fromArea(s, v) }
            customColorHue.onHueChanged = { h -> fromHue(h) }

            customColorHex.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = onHexEdited(s)
            })

            customColorNote.setText(if (ThemeManager.isDynamicAvailable) R.string.appearance_accent_note_dynamic else R.string.appearance_accent_fallback)
            customColorCancel.setTextColor(themeColors.getAccentColor(context))
            customColorReset.setTextColor(themeColors.getAccentColor(context))
            customColorReset.setOnClickListener { setColor(ColorMath.DEFAULT_CUSTOM, force = true) }
            customColorCancel.setOnClickListener { dismiss() }
            customColorApply.setOnClickListener { apply() }
        }
        setColor(color, force = true)
    }

    private fun rounded(fill: Int, context: android.content.Context) = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.button_background_backup_restore)!!
        .mutate().also { androidx.core.graphics.drawable.DrawableCompat.setTint(it, fill) }

    private fun fromArea(saturation: Float, brightness: Float) {
        val hue = binding.customColorHue.hue
        setColor(ColorMath.hsvToColor(hue, saturation, brightness), keepHue = true)
    }

    private fun fromHue(hue: Float) {
        val area = binding.customColorArea
        setColor(ColorMath.hsvToColor(hue, area.saturation, area.brightness), keepHue = true)
    }

    private fun onHexEdited(editable: Editable?) {
        if (updating || editable == null) return
        val text = editable.toString()
        val clean = ColorMath.sanitizeHexInput(text)
        if (clean != text) {
            updating = true
            editable.replace(0, editable.length, clean)
            updating = false
        }
        val parsed = if (clean.length == 7) ColorMath.parseHex(clean) else null
        if (parsed != null) {
            binding.customColorHexLayout.error = null
            setColor(parsed, fromHex = true)
        } else {
            binding.customColorHexLayout.error = if (clean.length >= 7) getString(R.string.appearance_accent_hex_error) else null
            binding.customColorApply.isEnabled = false
        }
    }

    // NOTE: Dragging the sliders must not rewrite hue from the color, since grey colors have no hue and the strip would jump to red.
    private fun setColor(newColor: Int, force: Boolean = false, keepHue: Boolean = false, fromHex: Boolean = false) {
        if (updating && !force) return
        updating = true
        color = newColor or 0xFF000000.toInt()
        with(binding) {
            val hsv = ColorMath.colorToHsv(color)
            val hue = if (keepHue || hsv[1] == 0f) customColorHue.hue else hsv[0]
            customColorHue.hue = hue
            customColorArea.hue = hue
            customColorArea.saturation = hsv[1]
            customColorArea.brightness = hsv[2]
            if (!fromHex) {
                val hex = ColorMath.toHex(color)
                if (customColorHex.text?.toString() != hex) customColorHex.setText(hex)
                customColorHexLayout.error = null
            }
            tint(customColorSampleSurface, color)
            tint(customColorSampleBlack, color)
            customColorApply.backgroundTintList = ColorStateList.valueOf(color)
            customColorApply.setTextColor(ColorMath.onColor(color))
            customColorApply.isEnabled = true
            paletteAdapter?.selected = color
            val onBlack = ColorMath.contrast(color, Color.BLACK)
            val onCard = ColorMath.contrast(color, themeColors.getBackgroundColorSecondary(requireContext()))
            val low = onBlack < LOW_CONTRAST || onCard < LOW_CONTRAST
            customColorContrast.text = getString(R.string.appearance_accent_contrast, onBlack, onCard) +
                if (low) " " + getString(R.string.appearance_accent_low_contrast) else ""
        }
        updating = false
    }

    private fun tint(view: android.widget.TextView, fill: Int) {
        view.background.mutate().let { androidx.core.graphics.drawable.DrawableCompat.setTint(it, fill) }
        view.setTextColor(ColorMath.onColor(fill))
    }

    private fun apply() {
        settings.themeAccent = ThemeManager.ACCENT_CUSTOM
        settings.monetColor = color
        dismiss()
        //The accent lives in the activity theme, so it shows only after the activity is rebuilt
        requireActivity().recreate()
    }

    private class PaletteAdapter(private val onPicked: (Int) -> Unit) : RecyclerView.Adapter<PaletteAdapter.Holder>() {

        var selected: Int = 0
            set(value) {
                if (field == value) return
                field = value
                notifyDataSetChanged()
            }

        class Holder(val root: FrameLayout, val circle: View, val check: ImageView) : RecyclerView.ViewHolder(root)

        override fun getItemCount() = ColorMath.PALETTE.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_palette_swatch, parent, false) as FrameLayout
            return Holder(view, view.findViewById(R.id.item_palette_circle), view.findViewById(R.id.item_palette_check))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val swatch = ColorMath.PALETTE[position]
            holder.circle.backgroundTintList = ColorStateList.valueOf(swatch)
            holder.circle.contentDescription = ColorMath.toHex(swatch)
            val isSelected = (swatch or 0xFF000000.toInt()) == (selected or 0xFF000000.toInt())
            holder.check.isVisible = isSelected
            holder.check.imageTintList = ColorStateList.valueOf(ColorMath.onColor(swatch))
            holder.circle.setOnClickListener { onPicked(swatch) }
            holder.circle.applyPressFeedback()
        }

    }

}
