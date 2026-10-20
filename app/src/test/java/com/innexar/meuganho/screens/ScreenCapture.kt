package com.innexar.meuganho.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.io.File

/**
 * Desenha a janela da Activity num bitmap e salva em app/build/screenshots/<nome>.png.
 * (captureToImage do Compose espera um quadro de GPU que o Robolectric não produz.)
 */
fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.saveScreen(name: String) {
    // O Room responde em threads próprias: damos tempo real para os Flows chegarem à tela.
    repeat(8) {
        Thread.sleep(150)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        waitForIdle()
    }
    val view: View = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
