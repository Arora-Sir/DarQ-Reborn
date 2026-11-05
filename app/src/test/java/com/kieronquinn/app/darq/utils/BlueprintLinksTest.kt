package com.kieronquinn.app.darq.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLDecoder

class BlueprintLinksTest {

    @Test
    fun upiLinkCarriesPayeeCurrencyAndNote() {
        assertEquals(
            "upi://pay?pa=mohit1998arora@yescred&pn=Mohit%20Arora&cu=INR&tn=DarQ%20Reborn%20Support",
            SupportHelper.upiUri()
        )
        assertEquals("upi://pay?pa=mohit1998arora@yescred&pn=Mohit%20Arora&cu=INR&tn=Other%20Support", SupportHelper.upiUri("Other"))
    }

    @Test
    fun obtainiumLinkEncodesTheBlueprintPayload() {
        val url = ObtainiumLink.url()
        val prefix = "https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/"
        assertTrue(url.startsWith(prefix))
        val json = URLDecoder.decode(url.removePrefix(prefix), "UTF-8")
        assertTrue(json.contains("\"id\":\"com.mohitarora.darqreborn\""))
        assertTrue(json.contains("\"url\":\"https://github.com/Arora-Sir/DarQ-Reborn\""))
        assertTrue(json.contains("\"author\":\"Arora-Sir\""))
        assertTrue(json.contains("\"name\":\"DarQ Reborn\""))
        //DarQ ships one universal APK, so no ABI filter may be set
        assertFalse(json.contains("arm64"))
        assertTrue(json.contains("versionExtractionRegEx"))
        assertFalse(url.contains(" "))
    }

    @Test
    fun developerLinksMatchTheBlueprint() {
        assertEquals("https://x.com/MohitAr0rA", Links.LINK_TWITTER)
        assertEquals("https://mohitarora.me", Links.LINK_WEBSITE)
        assertEquals("https://github.com/Arora-Sir", Links.LINK_GITHUB_PROFILE)
        assertEquals("https://www.linkedin.com/in/arora-sir", Links.LINK_LINKEDIN)
        assertEquals("https://paypal.me/arorasir", Links.LINK_DONATE)
        assertEquals("https://github.com/sponsors/Arora-Sir", Links.LINK_SPONSORS)
    }

    // NOTE: Guards the single-instance toast rule and the safe link rule. New code must go through Toasts and LinkOpener.
    @Test
    fun noDirectToastsOrBareLinkIntentsOutsideTheHelpers() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "Toasts.kt" || it.name == "LinkOpener.kt" || it.name == "Links.kt" }
            .flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    "Toast.makeText" in line || "URLSpan(" in line
                }.map { "${file.name}:${it.index + 1}" }
            }.toList()
        assertTrue("Use Toasts.show or LinkOpener instead: $offenders", offenders.isEmpty())
    }

}
