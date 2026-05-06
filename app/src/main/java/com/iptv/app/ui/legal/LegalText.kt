package com.iptv.app.ui.legal

import android.content.Context
import androidx.annotation.RawRes
import com.iptv.app.R

object LegalText {
    fun terms(context: Context): String = readRaw(context, R.raw.terms_pt)
    fun privacy(context: Context): String = readRaw(context, R.raw.privacy_pt)

    private fun readRaw(context: Context, @RawRes id: Int): String =
        context.resources.openRawResource(id).bufferedReader().use { it.readText() }
}
