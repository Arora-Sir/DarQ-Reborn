package com.kieronquinn.app.darq.components.xposed

import com.kieronquinn.app.darq.model.xposed.XposedSelfHooks

// NOTE: DarQ ships a legacy Xposed entry for old frameworks and a LibXposed entry for LSPosed 2.x and Vector.
// NOTE: The legacy entry proves itself through a self-hook in DarQ's own process, the modern one through the framework service binding.
object XposedState {

    val isActive: Boolean
        get() = XposedBridge.isActiveNow || XposedSelfHooks.isXposedModuleEnabled()

}
