package com.kieronquinn.app.darq.components.diagnostics

import androidx.core.content.FileProvider

/**
 *  Shares only the diagnostic log copy in cache/diagnostics-share, separate from the update download provider.
 */
class DiagnosticsFileProvider : FileProvider(com.kieronquinn.app.darq.R.xml.diagnostics_paths)
