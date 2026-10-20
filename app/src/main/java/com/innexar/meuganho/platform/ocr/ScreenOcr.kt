package com.innexar.meuganho.platform.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.innexar.meuganho.capture.core.ScreenLine
import java.util.concurrent.Executor

class OcrException(message: String) : Exception(message)

/**
 * Leitura da tela por imagem (OCR no aparelho, ML Kit offline). É o ÚLTIMO recurso:
 * só é chamado quando a árvore de acessibilidade não traz o texto da oferta.
 *
 * A captura fica em memória, é reconhecida e descartada na hora. Nunca é salva,
 * nunca é enviada. Máximo de uma captura a cada [MIN_INTERVAL_MS]; se já houver uma em andamento, a nova é ignorada.
 */
@RequiresApi(Build.VERSION_CODES.R)
class ScreenOcr(private val service: AccessibilityService, private val executor: Executor) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile private var busy = false
    @Volatile private var lastAt = 0L

    /** Miniatura da última captura reconhecida (para a vigia só rodar OCR quando a tela muda). */
    private var lastSignature: IntArray? = null

    /**
     * [excludes]: nosso painel e janelas de outros apps de corrida por cima (o texto delas é descartado).
     * [onlyIfChanged]: vigia visual; se a tela está igual à última leitura, nem roda o OCR.
     */
    fun capture(include: Rect?, excludes: List<Rect>, onlyIfChanged: Boolean = false, onResult: (Result<List<ScreenLine>>) -> Unit) {
        val now = System.currentTimeMillis()
        if (busy || now - lastAt < MIN_INTERVAL_MS) return
        busy = true
        lastAt = now
        service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(shot: AccessibilityService.ScreenshotResult) {
                val hardware = shot.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(hardware, shot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                hardware.close()
                if (bitmap == null) return finish(Result.failure(OcrException("captura vazia")), onResult)
                val signature = ScreenSignature.of(bitmap)
                if (onlyIfChanged && lastSignature?.let { ScreenSignature.similar(it, signature) } == true) {
                    bitmap.recycle()
                    busy = false
                    return
                }
                lastSignature = signature
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener(executor) { text ->
                        bitmap.recycle()
                        finish(Result.success(toLines(text, include, excludes)), onResult)
                    }
                    .addOnFailureListener(executor) { e ->
                        bitmap.recycle()
                        finish(Result.failure(e), onResult)
                    }
            }

            override fun onFailure(errorCode: Int) = finish(Result.failure(OcrException(describe(errorCode))), onResult)
        })
    }

    fun close() = recognizer.close()

    private fun finish(result: Result<List<ScreenLine>>, onResult: (Result<List<ScreenLine>>) -> Unit) {
        busy = false
        onResult(result)
    }

    /** Linhas em ordem de leitura (de cima para baixo, da esquerda para a direita). */
    /** Só linhas cujo centro está dentro da janela do app ([include]) e fora das áreas excluídas. */
    private fun toLines(text: Text, include: Rect?, excludes: List<Rect>): List<ScreenLine> =
        text.textBlocks.flatMap { it.lines }
            .filter { line ->
                val box = line.boundingBox ?: return@filter true
                (include == null || include.contains(box.centerX(), box.centerY())) && excludes.none { Rect.intersects(box, it) }
            }
            .sortedWith(compareBy({ (it.boundingBox?.top ?: 0) / ROW_TOLERANCE_PX }, { it.boundingBox?.left ?: 0 }))
            .map { ScreenLine(it.text) }

    private fun describe(code: Int): String = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            "sem permissão de captura: desligue e ligue a Acessibilidade do MeuGanho"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "o app bloqueia captura de tela (janela segura)"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "capturas muito seguidas"
        else -> "falha na captura ($code)"
    }

    private companion object {
        // O Android aceita ~3 capturas/s; 700 ms deixa folga e permite a rajada 0 / 0,8 / 1,8 s.
        const val MIN_INTERVAL_MS = 700L
        const val ROW_TOLERANCE_PX = 24
    }
}
