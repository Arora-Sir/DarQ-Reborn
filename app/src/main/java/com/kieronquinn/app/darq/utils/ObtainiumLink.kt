package com.kieronquinn.app.darq.utils

import java.net.URLEncoder

/**
 *  The one-tap link that adds DarQ to Obtainium, which then tracks the GitHub releases and notifies about new ones.
 */
object ObtainiumLink {

    private const val REDIRECT = "https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/"
    private const val PACKAGE = "com.mohitarora.darqreborn"

    // NOTE: DarQ ships a single universal APK, so no ABI filter is set. Release tags look like v3.2.0.
    private val ADDITIONAL_SETTINGS = """{"versionExtractionRegEx":"v(.+)","matchGroupToUse":"$1"}"""

    private fun jsonString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun payload(): String = buildString {
        append('{')
        append("\"id\":").append(jsonString(PACKAGE)).append(',')
        append("\"url\":").append(jsonString(Links.LINK_GITHUB)).append(',')
        append("\"author\":").append(jsonString("Arora-Sir")).append(',')
        append("\"name\":").append(jsonString("DarQ Reborn")).append(',')
        append("\"additionalSettings\":").append(jsonString(ADDITIONAL_SETTINGS))
        append('}')
    }

    fun url(): String = REDIRECT + URLEncoder.encode(payload(), "UTF-8").replace("+", "%20")

}
