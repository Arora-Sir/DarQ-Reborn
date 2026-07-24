package com.kieronquinn.app.darq.ui.screens.settings.autodark

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.kieronquinn.app.darq.R
import com.kieronquinn.app.darq.databinding.FragmentSettingsAutoDarkBinding
import com.kieronquinn.app.darq.model.settings.SettingsItem
import com.kieronquinn.app.darq.service.autodark.DarqAutoDarkForegroundService
import com.kieronquinn.app.darq.ui.base.AutoExpandOnRotate
import com.kieronquinn.app.darq.ui.base.BackAvailable
import com.kieronquinn.app.darq.ui.screens.settings.BaseSettingsFragment
import com.kieronquinn.app.darq.ui.screens.settings.SettingsAdapter
import com.kieronquinn.app.darq.utils.AutoDarkUtils
import com.kieronquinn.app.darq.work.DarqSunriseSunsetWork
import com.kieronquinn.monetcompat.extensions.views.applyMonetRecursively

class SettingsAutoDarkFragment : BaseSettingsFragment<FragmentSettingsAutoDarkBinding>(
    FragmentSettingsAutoDarkBinding::inflate
), AutoExpandOnRotate, BackAvailable {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            settings.useLocation = true
            rescheduleAutoDark(justReschedule = false)
        } else {
            settings.useLocation = false
        }
        binding.recyclerView.post {
            adapter.notifyDataSetChanged()
        }
    }

    private val startTimeSettingItem: SettingsItem.Setting by lazy {
        SettingsItem.Setting(
            R.drawable.ic_sun,
            getString(R.string.item_auto_dark_start_time_title),
            AutoDarkUtils.formatMinutesFromMidnight(requireContext(), settings.autoDarkStartTime),
            visible = { settings.autoDarkTheme && settings.autoDarkScheduleMode == 2 },
            tapAction = {
                showTimePicker(settings.autoDarkStartTime) { newMins ->
                    settings.autoDarkStartTime = newMins
                    startTimeSettingItem.content = AutoDarkUtils.formatMinutesFromMidnight(requireContext(), newMins)
                    rescheduleAutoDark(justReschedule = false)
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        )
    }

    private val endTimeSettingItem: SettingsItem.Setting by lazy {
        SettingsItem.Setting(
            R.drawable.ic_night,
            getString(R.string.item_auto_dark_end_time_title),
            AutoDarkUtils.formatMinutesFromMidnight(requireContext(), settings.autoDarkEndTime),
            visible = { settings.autoDarkTheme && settings.autoDarkScheduleMode == 2 },
            tapAction = {
                showTimePicker(settings.autoDarkEndTime) { newMins ->
                    settings.autoDarkEndTime = newMins
                    endTimeSettingItem.content = AutoDarkUtils.formatMinutesFromMidnight(requireContext(), newMins)
                    rescheduleAutoDark(justReschedule = false)
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        )
    }

    override val settingsItems: MutableList<SettingsItem> by lazy {
        listOf<SettingsItem>(
            // Section 1: Master Control
            SettingsItem.Header(
                getString(R.string.item_auto_dark_header_master)
            ),
            SettingsItem.SwitchSetting(
                icon = R.drawable.ic_day_night_auto_round,
                title = getString(R.string.item_auto_dark_master_title),
                content = getString(R.string.item_auto_dark_master_content),
                setting = settings::autoDarkTheme,
                tapAction = { _ ->
                    rescheduleAutoDark(justReschedule = false)
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                    true
                }
            ),

            // Section 2: Schedule Mode (Custom Time Schedule listed FIRST)
            SettingsItem.Header(
                getString(R.string.item_auto_dark_header_mode),
                visible = { settings.autoDarkTheme }
            ),
            SettingsItem.SwitchSetting(
                icon = R.drawable.ic_boot_wait,
                title = getString(R.string.item_auto_dark_mode_custom_title),
                content = getString(R.string.item_auto_dark_mode_custom_content),
                getValue = { settings.autoDarkScheduleMode == 2 },
                setValue = { checked ->
                    if (checked) {
                        settings.autoDarkScheduleMode = 2
                    } else if (settings.autoDarkScheduleMode == 2) {
                        settings.autoDarkScheduleMode = 1
                    }
                },
                visible = { settings.autoDarkTheme },
                tapAction = { _ ->
                    rescheduleAutoDark(justReschedule = false)
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                    true
                }
            ),
            SettingsItem.SwitchSetting(
                icon = R.drawable.ic_sunset,
                title = getString(R.string.item_auto_dark_mode_sun_title),
                content = getString(R.string.item_auto_dark_mode_sun_content),
                getValue = { settings.autoDarkScheduleMode == 1 },
                setValue = { checked ->
                    if (checked) {
                        settings.autoDarkScheduleMode = 1
                    } else if (settings.autoDarkScheduleMode == 1) {
                        settings.autoDarkScheduleMode = 2
                    }
                },
                visible = { settings.autoDarkTheme },
                tapAction = { _ ->
                    rescheduleAutoDark(justReschedule = false)
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                    true
                }
            ),

            // Section 3: Sunset & Sunrise Options
            SettingsItem.Header(
                getString(R.string.item_auto_dark_header_sunset_options),
                visible = { settings.autoDarkTheme && settings.autoDarkScheduleMode == 1 }
            ),
            SettingsItem.SwitchSetting(
                icon = R.drawable.ic_location,
                title = getString(R.string.item_use_location_title),
                content = getString(R.string.item_use_location_content),
                setting = settings::useLocation,
                visible = { settings.autoDarkTheme && settings.autoDarkScheduleMode == 1 },
                tapAction = { checked ->
                    if (checked) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasPermission) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            settings.useLocation = true
                            rescheduleAutoDark(justReschedule = false)
                        }
                    } else {
                        settings.useLocation = false
                        rescheduleAutoDark(justReschedule = false)
                    }
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                    true
                }
            ),

            // Section 4: Custom Time Options
            SettingsItem.Header(
                getString(R.string.item_auto_dark_header_custom_options),
                visible = { settings.autoDarkTheme && settings.autoDarkScheduleMode == 2 }
            ),
            startTimeSettingItem,
            endTimeSettingItem,

            // Section 5: Schedule Target (DarQ Force Dark Only listed FIRST)
            SettingsItem.Header(
                getString(R.string.item_auto_dark_header_target),
                visible = { settings.autoDarkTheme }
            ),
            SettingsItem.SwitchSetting(
                icon = R.drawable.ic_app_whitelist,
                title = getString(R.string.item_auto_dark_target_darq_title),
                content = getString(R.string.item_auto_dark_target_darq_content),
                getValue = { settings.autoDarkTargetMode == 1 },
                setValue = { checked ->
                    if (checked) {
                        settings.autoDarkTargetMode = 1
                    } else if (settings.autoDarkTargetMode == 1) {
                        settings.autoDarkTargetMode = 0
                    }
                },
                visible = { settings.autoDarkTheme },
                tapAction = { _ ->
                    rescheduleAutoDark(justReschedule = false)
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                    true
                }
            ),
            SettingsItem.SwitchSetting(
                icon = R.drawable.ic_system_theme,
                title = getString(R.string.item_auto_dark_target_system_title),
                content = getString(R.string.item_auto_dark_target_system_content),
                getValue = { settings.autoDarkTargetMode == 0 },
                setValue = { checked ->
                    if (checked) {
                        settings.autoDarkTargetMode = 0
                    } else if (settings.autoDarkTargetMode == 0) {
                        settings.autoDarkTargetMode = 1
                    }
                },
                visible = { settings.autoDarkTheme },
                tapAction = { _ ->
                    rescheduleAutoDark(justReschedule = false)
                    binding.recyclerView.post {
                        adapter.notifyDataSetChanged()
                    }
                    true
                }
            )
        ).toMutableList()
    }

    private val adapter: SettingsAdapter by lazy {
        SettingsAdapter(requireContext(), settingsItems)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        view?.applyMonetRecursively()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView(binding.recyclerView, adapter)
        setupSnackbarPadding(binding.recyclerView)
    }

    private fun rescheduleAutoDark(justReschedule: Boolean = false) {
        try {
            val intent = Intent(requireContext(), DarqAutoDarkForegroundService::class.java).apply {
                putExtra(DarqAutoDarkForegroundService.KEY_JUST_RESCHEDULE, justReschedule)
            }
            requireContext().startForegroundService(intent)
        } catch (e: Exception) {
            android.util.Log.e("SettingsAutoDark", "Failed to reschedule auto dark service", e)
        }
    }

    private fun showTimePicker(initialMinutes: Int, onTimeSelected: (Int) -> Unit) {
        val is24HourFormat = android.text.format.DateFormat.is24HourFormat(requireContext())
        val clockFormat = if (is24HourFormat) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(clockFormat)
            .setHour(initialMinutes / 60)
            .setMinute(initialMinutes % 60)
            .setTitleText(getString(R.string.dialog_select_time))
            .build()

        picker.addOnPositiveButtonClickListener {
            val newMins = picker.hour * 60 + picker.minute
            onTimeSelected(newMins)
        }

        picker.show(childFragmentManager, "auto_dark_time_picker")
    }

}
