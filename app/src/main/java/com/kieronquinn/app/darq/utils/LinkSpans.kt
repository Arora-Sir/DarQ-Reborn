package com.kieronquinn.app.darq.utils

import android.content.Context
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View

/**
 *  Replaces every URLSpan in a text with a span that opens the link through [LinkOpener].
 *  A plain URLSpan calls startActivity without a catch, so a phone with no browser would crash on tap.
 */
object LinkSpans {

    fun safe(context: Context, text: CharSequence): CharSequence {
        val appContext = context.applicationContext
        val result = SpannableString(text)
        for (span in result.getSpans(0, result.length, URLSpan::class.java)) {
            val start = result.getSpanStart(span)
            val end = result.getSpanEnd(span)
            val flags = result.getSpanFlags(span)
            val url = span.url
            result.removeSpan(span)
            result.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    LinkOpener.open(appContext, url)
                }
            }, start, end, flags)
        }
        return result
    }

}
