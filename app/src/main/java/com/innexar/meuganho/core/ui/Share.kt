package com.innexar.meuganho.core.ui

import android.content.Context
import android.content.Intent

/** Abre o compartilhamento do Android com um texto (resumo do dia, fixture de diagnóstico). */
fun Context.shareText(text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, title))
}
