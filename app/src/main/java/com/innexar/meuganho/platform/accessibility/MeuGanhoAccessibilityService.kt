package com.innexar.meuganho.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.innexar.meuganho.capture.core.TextPatterns
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.platform.ocr.ScreenOcr
import com.innexar.meuganho.app.appContainer
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.engine.ServiceStatus
import com.innexar.meuganho.platform.overlay.OverlayController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lê as telas do Uber Driver e do 99 Motorista (só esses pacotes, ver
 * res/xml/accessibility_service_config.xml). NUNCA executa cliques: aceitar ou
 * recusar é decisão do motorista.
 *
 * Eventos chegam em rajadas (dezenas por segundo durante animações). Em vez de
 * ler a árvore a cada evento, agrupamos: o primeiro evento agenda UMA leitura
 * 250 ms depois; os seguintes nessa janela são absorvidos. A leitura roda numa
 * thread própria para não disputar a main thread com o overlay.
 */
class MeuGanhoAccessibilityService : AccessibilityService() {

    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private val scope = MainScope()
    private var overlay: OverlayController? = null
    private var ocr: Any? = null // ScreenOcr (API 30+); Any para não carregar a classe em Android antigo.

    @Volatile private var pendingPackage: String? = null
    @Volatile private var readScheduled = false
    private val versionCache = HashMap<String, Pair<Long, Long>>()

    private val readRunnable = Runnable { readActiveScreen() }

    /** Verificação de atualização e de regras de leitura a cada 30 min (fora desta thread: aqui só leitura de tela). */
    private val updateCheck: Runnable = object : Runnable {
        override fun run() {
            appContainer.updateManager.checkIfDue()
            // Rede NUNCA na thread de captura: com internet lenta, travaria a leitura das ofertas.
            appContainer.appScope.launch(kotlinx.coroutines.Dispatchers.IO) { appContainer.remoteRules.refreshIfDue() }
            handler.postDelayed(this, UPDATE_CHECK_EVERY_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        worker = HandlerThread("meuganho-capture").also { it.start() }
        handler = Handler(worker.looper)
        ServiceStatus.accessibilityConnected.value = true
        appContainer.remoteRules // carrega as regras salvas antes da primeira leitura
        overlay = OverlayController(this, appContainer).also { it.start(scope) }
        // O serviço fica ativo o dia todo: é ele que descobre versões novas mesmo com o app fechado.
        handler.post(updateCheck)
        getSystemService(android.media.AudioManager::class.java)?.registerAudioPlaybackCallback(soundTrigger, handler)
        // Oferta lida pela metade: nova captura em 350 ms.
        scope.launch {
            appContainer.captureCoordinator.ocrRetry.collect { platform ->
                val pkg = platform.packageNames.firstOrNull { isVisibleOnTop(it) } ?: return@collect
                ocrPackage = pkg
                handler.postDelayed({ runOcr(onlyIfChanged = false) }, OCR_RETRY_MS)
            }
        }
        AppLog.d(LogCategory.CAPTURE, "Acessibilidade conectada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in Platform.allPackages) return
        pendingPackage = pkg
        if (!readScheduled && ::handler.isInitialized) {
            readScheduled = true
            handler.postDelayed(readRunnable, COALESCE_MS)
        }
    }

    private fun readActiveScreen() {
        readScheduled = false
        val pkg = pendingPackage ?: return
        try {
            // Todas as janelas do app: o cartão de oferta costuma ser uma janela separada do mapa.
            val roots = findRoots(pkg)
            val reads = roots.mapIndexed { i, root -> NodeTreeReader.read(root, groupOffset = i * 10_000) }
            val lines = reads.flatMap { it.lines }
            val debug = "windows=${roots.size} " + reads.joinToString(" | ") { it.stats.toString() } +
                if (Platform.fromPackage(pkg)?.needsOcr == true) " census=${windowCensus()}" else ""
            val now = System.currentTimeMillis()
            appContainer.captureCoordinator.submit(ScreenSnapshot(pkg, versionOf(pkg), now, lines, debug = debug))
            maybeOcr(pkg, lines, now)
        } catch (e: Exception) {
            // Nós de acessibilidade podem ficar inválidos no meio da leitura (tela trocou).
            AppLog.e(LogCategory.CAPTURE, "Falha lendo tela", e)
        }
    }

    /**
     * OCR de reserva: só para plataformas que escondem o texto (99), só quando a árvore
     * não trouxe NENHUM valor em R$, e só se o app estiver visível por cima
     * (a captura pega a tela inteira; não podemos atribuir à 99 a tela de outro app).
     *
     * Rajada: captura agora e de novo a +0,8 s e +1,8 s. O cartão de oferta costuma
     * terminar de aparecer (animação) DEPOIS do evento que nos acordou; sem a rajada,
     * a oferta esperava o próximo evento ou era perdida. Um evento novo reinicia a rajada,
     * então um fluxo contínuo de eventos não multiplica capturas.
     */
    private fun maybeOcr(pkg: String, lines: List<com.innexar.meuganho.capture.core.ScreenLine>, now: Long) {
        val platform = Platform.fromPackage(pkg) ?: return
        if (!platform.needsOcr || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (lines.any { TextPatterns.moneyIn(it.text).isNotEmpty() }) return
        ocrPackage = pkg
        handler.removeCallbacks(ocrNow)
        OCR_BURST_MS.forEach { handler.postDelayed(ocrNow, it) }
        if (!watching) {
            watching = true
            handler.postDelayed(ocrWatch, OCR_WATCH_EVERY_MS)
        }
    }

    @Volatile private var watching = false

    /**
     * Gatilho sonoro: a 99 toca um som quando chega oferta. O Android avisa que UM som começou
     * (sem dizer de qual app nem dar acesso ao áudio); se a 99 estiver na tela nesse instante,
     * disparamos a leitura na hora. É o gatilho mais preciso: o som toca junto com a oferta.
     */
    private val soundTrigger = object : android.media.AudioManager.AudioPlaybackCallback() {
        private var playing = 0

        override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>) {
            val now = configs.size
            if (now > playing) onSoundStarted()
            playing = now
        }
    }

    private fun onSoundStarted() {
        val pkg = Platform.entries.filter { it.needsOcr }.flatMap { it.packageNames }.firstOrNull { isVisibleOnTop(it) } ?: return
        Platform.fromPackage(pkg)?.let {
            appContainer.diagnostics.onSoundTrigger(it)
            appContainer.captureCoordinator.onOfferSound(it, System.currentTimeMillis())
        }
        ocrPackage = pkg
        handler.removeCallbacks(ocrNow)
        OCR_BURST_MS.forEach { handler.postDelayed(ocrNow, it) }
    }

    /**
     * Vigia visual: a 99 desenha a oferta como imagem e nem sempre avisa (evento) que a tela
     * mudou. Enquanto ela estiver na frente, a cada 1,5 s comparamos uma miniatura da tela;
     * o OCR só roda quando a imagem mudou de verdade. Com a 99 fora da tela, a vigia para.
     */
    private val ocrWatch: Runnable = object : Runnable {
        override fun run() {
            val pkg = ocrPackage
            if (pkg == null || !isVisibleOnTop(pkg)) {
                watching = false
                return
            }
            runOcr(onlyIfChanged = true)
            // Turno aberto = motorista esperando oferta: vigia a cada 0,7 s. Sem turno, 1,5 s.
            val every = if (com.innexar.meuganho.engine.ServiceStatus.shiftServiceRunning.value) OCR_WATCH_SHIFT_MS else OCR_WATCH_EVERY_MS
            handler.postDelayed(this, every)
        }
    }

    @Volatile private var ocrPackage: String? = null
    private val ocrNow = Runnable { runOcr(onlyIfChanged = false) }

    private fun runOcr(onlyIfChanged: Boolean) {
        val pkg = ocrPackage ?: return
        val platform = Platform.fromPackage(pkg) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !isVisibleOnTop(pkg)) return
        val reader = (ocr as? ScreenOcr) ?: ScreenOcr(this) { handler.post(it) }.also { ocr = it }
        val startedAt = System.currentTimeMillis()
        val (own, above) = ocrRegions(pkg)
        reader.capture(own, listOfNotNull(overlay?.screenRect()) + above, onlyIfChanged) { result ->
            val tookMs = System.currentTimeMillis() - startedAt
            appContainer.diagnostics.onOcr(platform, result.isSuccess, tookMs)
            result.onSuccess { ocrLines ->
                if (ocrLines.isNotEmpty()) {
                    appContainer.captureCoordinator.submit(
                        ScreenSnapshot(pkg, versionOf(pkg), startedAt, ocrLines, CaptureChannel.OCR,
                            debug = "ocr lines=${ocrLines.size} ms=$tookMs")
                    )
                }
            }.onFailure {
                AppLog.e(LogCategory.CAPTURE, "OCR ${platform.label}: ${it.message}")
                appContainer.diagnostics.onParserError(platform, "OCR: ${it.message}", startedAt)
            }
        }
    }

    /**
     * O app está visível por cima dos demais. Percorre as janelas da mais alta para a mais
     * baixa. Qualquer janela de APLICATIVO de outro dono na frente (inclusive o próprio
     * MeuGanho, o launcher e a tela de apps recentes) = não visível. Janelas de sistema
     * pequenas (barras, painel lateral) e cartões de outros apps de corrida (recortados pelo
     * OCR) não bloqueiam; uma janela de sistema que cobre a tela (cortina de notificações) bloqueia.
     */
    private fun isVisibleOnTop(pkg: String): Boolean {
        val screenArea = resources.displayMetrics.let { it.widthPixels.toLong() * it.heightPixels }
        val bounds = android.graphics.Rect()
        for (w in windows.orEmpty()) {
            if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD || w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val owner = w.root?.packageName?.toString()
            if (owner == pkg) return true
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) return false
            if (owner in Platform.allPackages) continue
            // Só a cortina de notificações (interface do sistema cobrindo a tela) bloqueia.
            // Janelas de outros serviços, como o painel lateral da Samsung (cocktailbarservice),
            // são transparentes e podem ocupar a tela inteira: não escondem a 99.
            if (owner != "com.android.systemui") continue
            w.getBoundsInScreen(bounds)
            if (bounds.width().toLong() * bounds.height() > screenArea / 2) return false
        }
        return false
    }

    /**
     * Onde o OCR pode ler: dentro da janela mais alta do próprio app ([own]); fora das janelas de
     * OUTROS apps de corrida que estão POR CIMA dela ([above]). Real (29/09/2026): com o Uber aberto,
     * a oferta da 99 é um cartão flutuante ("sys:com.app99.driver > app:com.ubercab.driver"): ler só
     * o retângulo do cartão; o Uber por baixo não entra e não apaga o cartão.
     */
    private fun ocrRegions(pkg: String): Pair<android.graphics.Rect?, List<android.graphics.Rect>> {
        val above = mutableListOf<android.graphics.Rect>()
        val screenArea = resources.displayMetrics.let { it.widthPixels.toLong() * it.heightPixels }
        var own: android.graphics.Rect? = null
        for (w in windows.orEmpty()) {
            val owner = w.root?.packageName?.toString() ?: continue
            val rect = android.graphics.Rect().also { w.getBoundsInScreen(it) }
            // Barra de status e notificações flutuantes (inclusive a do MeuGanho com a análise da oferta):
            // real 01/10/2026, o OCR lia "99 R$ 6,10 / R$ 6,52/km / R$2/h" da NOSSA notificação como se fosse da 99.
            if (owner == "com.android.systemui" && rect.width().toLong() * rect.height() < screenArea / 2) { above += rect; continue }
            if (own == null && owner == pkg) own = rect
            else if (own == null && owner in Platform.allPackages) above += rect
        }
        return own to above
    }

    /**
     * Censo das janelas na tela (da mais alta para a mais baixa): tipo, pacote dono e se o
     * conteúdo é legível. Sem texto. Serve para provar por que a 99 não é lida pela Acessibilidade.
     */
    private fun windowCensus(): String = windows.orEmpty().joinToString(";") { w ->
        val root = w.root
        val type = when (w.type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "app"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "sys"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "ime"
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "a11y"
            else -> "t${w.type}"
        }
        val content = when {
            root == null -> "root=null"
            else -> "children=${root.childCount}"
        }
        "$type:${root?.packageName ?: "?"}:$content:title=${w.title?.toString()?.take(24) ?: "-"}"
    }

    /** Raízes de TODAS as janelas do pacote (máx. 4), na ordem de camadas do sistema. */
    private fun findRoots(pkg: String): List<AccessibilityNodeInfo> {
        val roots = windows.orEmpty().mapNotNull { w -> w.root?.takeIf { it.packageName == pkg } }.take(MAX_WINDOWS)
        return roots.ifEmpty { listOfNotNull(rootInActiveWindow?.takeIf { it.packageName == pkg }) }
    }

    /** versionCode do app de origem, com cache de 10 min: vincula quebras de parser a atualizações. */
    private fun versionOf(pkg: String): Long {
        val now = System.currentTimeMillis()
        versionCache[pkg]?.let { (version, at) -> if (now - at < 600_000) return version }
        val version = try {
            val info = packageManager.getPackageInfo(pkg, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        }
        versionCache[pkg] = version to now
        return version
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { getSystemService(android.media.AudioManager::class.java)?.unregisterAudioPlaybackCallback(soundTrigger) }
        ServiceStatus.accessibilityConnected.value = false
        overlay?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) (ocr as? ScreenOcr)?.close()
        scope.cancel()
        if (::worker.isInitialized) {
            handler.removeCallbacksAndMessages(null)
            worker.quitSafely()
        }
        super.onDestroy()
    }

    private companion object {
        const val COALESCE_MS = 250L
        const val MAX_WINDOWS = 4
        val OCR_BURST_MS = longArrayOf(0L, 800L, 1_800L)
        const val OCR_WATCH_EVERY_MS = 1_500L
        const val OCR_WATCH_SHIFT_MS = 700L
        const val OCR_RETRY_MS = 350L
        const val UPDATE_CHECK_EVERY_MS = 30 * 60 * 1000L
    }
}
