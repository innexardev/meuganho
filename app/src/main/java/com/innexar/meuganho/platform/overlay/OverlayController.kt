package com.innexar.meuganho.platform.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.data.preferences.AppSettings
import com.innexar.meuganho.data.repository.DayOverview
import com.innexar.meuganho.engine.OverlayOffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Overlay sobre Uber/99, em três modos:
 * - PANEL: painel com o resumo do dia ou a(s) oferta(s);
 * - BUBBLE: bolinha pequena (✕ do painel), toque reabre;
 * - CLOSED: arrastado até o alvo ✕ do rodapé. Some até a próxima oferta ou o próximo turno.
 * Ofertas SEMPRE abrem o painel: é para isso que ele existe. Depois voltam ao modo escolhido.
 *
 * Usa TYPE_ACCESSIBILITY_OVERLAY (dispensa a permissão "sobrepor a outros apps") e
 * nunca intercepta toques fora das próprias janelas.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val container: AppContainer,
) {
    private enum class Mode { PANEL, BUBBLE, CLOSED }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val panel = OverlayView(service)
    private val bubble = OverlayBubble(service)
    private val closeTarget = OverlayCloseTarget(service)
    private val main = Handler(Looper.getMainLooper())
    private val hideOffer = Runnable { container.captureCoordinator.dismissOverlayOffer() }
    private var job: Job? = null

    private var mode = Mode.PANEL
    private var shown: View? = null
    private var targetShown = false
    private var currentOffers: List<OverlayOffer> = emptyList()
    private var lastModel: Model? = null
    private var lastShiftOpen = false
    private var lastNotifiedAt = 0L

    private val params = layoutParams()
    private val targetParams = layoutParams().apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; x = 0; y = dp(48) }

    private data class Model(val settings: AppSettings, val shiftOpen: Boolean, val day: DayOverview?, val offers: List<OverlayOffer>)

    fun start(scope: CoroutineScope) {
        installDrag(panel) { onPanelTap() }
        installDrag(bubble) { setMode(Mode.PANEL) }
        panel.close.setOnClickListener { setMode(Mode.BUBBLE) }
        job = scope.launch {
            combine(
                container.settings,
                container.shiftRepository.openShift,
                container.statsRepository.observeToday(),
                container.captureCoordinator.overlayOffers,
            ) { settings, shift, day, offers -> Model(settings, shift != null, day, offers) }
                .distinctUntilChanged()
                // Cálculo do resumo fora da main thread; só o render das Views roda nela.
                .flowOn(Dispatchers.Default)
                .collect { render(it) }
        }
    }

    /** Área visível do painel/bolinha, para o OCR ignorar o nosso próprio texto. */
    fun screenRect(): Rect? {
        val v = shown ?: return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    fun stop() {
        job?.cancel()
        main.removeCallbacks(hideOffer)
        show(null)
        hideTarget()
    }

    private fun render(m: Model) {
        lastModel = m
        currentOffers = m.offers
        // Turno novo: um painel fechado no turno anterior volta.
        if (m.shiftOpen && !lastShiftOpen && mode == Mode.CLOSED) mode = Mode.PANEL
        lastShiftOpen = m.shiftOpen
        if (!m.shiftOpen && m.offers.isEmpty()) return show(null)

        val display = if (m.settings.overlayEnabled) m.settings.offerDisplay else com.innexar.meuganho.data.preferences.OfferDisplay.NOTIFICATION
        if (m.offers.isNotEmpty() && m.offers.maxOf { it.shownAt } > lastNotifiedAt &&
            display != com.innexar.meuganho.data.preferences.OfferDisplay.PANEL
        ) {
            lastNotifiedAt = m.offers.maxOf { it.shownAt }
            com.innexar.meuganho.platform.notifications.OfferNotifier.show(service, m.offers, m.settings.thresholds)
        }
        // Modo só-notificação: o painel flutuante não aparece.
        if (display == com.innexar.meuganho.data.preferences.OfferDisplay.NOTIFICATION) return show(null)
        if (m.offers.isNotEmpty()) {
            panel.render(OverlayFormatter.offers(m.offers))
            main.removeCallbacks(hideOffer)
            val remaining = OFFER_VISIBLE_MS - (System.currentTimeMillis() - m.offers.maxOf { it.shownAt })
            main.postDelayed(hideOffer, remaining.coerceAtLeast(1_000))
            return show(panel, m.settings)
        }
        panel.render(OverlayFormatter.summary(m.day, m.settings.overlayExpanded))
        bubble.text = m.day?.summary?.centsPerHourOnline?.let { "${Money.formatShort(it)}\n/h" } ?: "MG"
        when (mode) {
            Mode.PANEL -> show(panel, m.settings)
            Mode.BUBBLE -> show(bubble, m.settings)
            Mode.CLOSED -> show(null)
        }
    }

    private fun setMode(next: Mode) {
        mode = next
        if (currentOffers.isNotEmpty() && next != Mode.PANEL) container.captureCoordinator.dismissOverlayOffer()
        lastModel?.let { render(it.copy(offers = if (next == Mode.PANEL) it.offers else emptyList())) }
    }

    private fun onPanelTap() {
        if (currentOffers.isNotEmpty()) {
            container.captureCoordinator.dismissOverlayOffer()
            return
        }
        container.appScope.launch { container.settingsRepository.update { it.copy(overlayExpanded = !it.overlayExpanded) } }
    }

    /** Mostra só a view pedida (ou nenhuma), trocando entre painel e bolinha no mesmo lugar. */
    private fun show(view: View?, settings: AppSettings? = null) {
        if (shown === view) return
        shown?.let { runCatching { windowManager.removeView(it) } }
        shown = null
        if (view == null) return
        if (settings != null && params.x == UNSET) {
            params.x = settings.overlayX
            params.y = settings.overlayY
        }
        try {
            windowManager.addView(view, params)
            shown = view
        } catch (e: Exception) {
            AppLog.e(LogCategory.OVERLAY, "Não foi possível exibir o overlay", e)
        }
    }

    /** Arrasta em qualquer direção (limitado às bordas); toque simples executa [onTap]. */
    @SuppressLint("ClickableViewAccessibility")
    private fun installDrag(view: View, onTap: () -> Unit) {
        val slop = ViewConfiguration.get(service).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) {
                        dragging = true
                        showTarget()
                    }
                    if (dragging && shown === v) {
                        val screen = screenSize()
                        params.x = (startX + dx.toInt()).coerceIn(0, (screen.first - v.width).coerceAtLeast(0))
                        params.y = (startY + dy.toInt()).coerceIn(0, (screen.second - v.height).coerceAtLeast(0))
                        windowManager.updateViewLayout(v, params)
                        closeTarget.highlight(overTarget(e.rawX, e.rawY))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val close = dragging && overTarget(e.rawX, e.rawY)
                    hideTarget()
                    when {
                        close -> setMode(Mode.CLOSED)
                        dragging -> savePosition()
                        e.actionMasked == MotionEvent.ACTION_UP -> onTap()
                    }
                }
            }
            true
        }
    }

    private fun showTarget() {
        if (targetShown) return
        runCatching { windowManager.addView(closeTarget, targetParams); targetShown = true }
        closeTarget.highlight(false)
    }

    private fun hideTarget() {
        if (!targetShown) return
        runCatching { windowManager.removeView(closeTarget) }
        targetShown = false
    }

    /** O dedo está sobre o alvo ✕ (com folga, para acertar sem precisão). */
    private fun overTarget(rawX: Float, rawY: Float): Boolean {
        if (!targetShown) return false
        val loc = IntArray(2)
        closeTarget.getLocationOnScreen(loc)
        val margin = dp(36)
        return rawX >= loc[0] - margin && rawX <= loc[0] + closeTarget.width + margin &&
            rawY >= loc[1] - margin && rawY <= loc[1] + closeTarget.height + margin
    }

    private fun screenSize(): Pair<Int, Int> {
        val m = service.resources.displayMetrics
        return m.widthPixels to m.heightPixels
    }

    private fun savePosition() {
        val x = params.x
        val y = params.y
        container.appScope.launch { container.settingsRepository.update { it.copy(overlayX = x, overlayY = y) } }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = UNSET
    }

    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val OFFER_VISIBLE_MS = 15_000L
        const val UNSET = Int.MIN_VALUE
    }
}
