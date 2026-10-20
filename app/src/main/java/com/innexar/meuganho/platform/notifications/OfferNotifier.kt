package com.innexar.meuganho.platform.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.innexar.meuganho.R
import com.innexar.meuganho.app.MainActivity
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.domain.financial.OfferComparator
import com.innexar.meuganho.domain.financial.RatingThresholds
import com.innexar.meuganho.domain.model.OfferRating
import com.innexar.meuganho.engine.OverlayOffer

/**
 * Oferta analisada como notificação no topo da tela (no lugar do painel flutuante):
 * não cobre o botão Aceitar, aparece sobre qualquer app e some sozinha.
 * Uma notificação por plataforma; com Uber e 99 juntos, a melhor ganha "★ MELHOR".
 */
object OfferNotifier {
    private const val BASE_ID = 500
    // A oferta fica ~15 s na tela; 7 s basta para decidir e não acumula com a próxima.
    private const val TIMEOUT_MS = 7_000L

    fun show(context: Context, offers: List<OverlayOffer>, thresholds: RatingThresholds) {
        if (!allowed(context) || offers.isEmpty()) return
        val best = OfferComparator.bestIndex(offers.map { it.analysis })?.takeIf { offers.size > 1 }?.let { offers[it] }
        offers.groupBy { it.platform }.forEach { (platform, list) ->
            val o = list.first()
            val text = OfferNotificationText.of(o, thresholds, isBest = o === best, extra = list.size - 1)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(context, NotificationChannels.OFFERS)
                .setSmallIcon(R.drawable.ic_stat_meuganho)
                .setContentTitle(text.title)
                .setContentText(text.metrics)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${text.metrics}\n${text.details}"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setTimeoutAfter(TIMEOUT_MS)
                .setAutoCancel(true)
                // SEM setSilent(): ele marca a notificação como "não alerta" e o Android deixa de mostrá-la
                // no topo. O silêncio vem do CANAL (sem som e sem vibração), que mantém o pop-up.
                .setDefaults(0)
                .setContentIntent(open)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(BASE_ID + platform.ordinal, n)
        }
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/** Textos da notificação de oferta (separados para teste). Bolinha por métrica, pelos limites pessoais. */
data class OfferNotificationText(val title: String, val metrics: String, val details: String) {
    companion object {
        fun of(o: OverlayOffer, t: RatingThresholds, isBest: Boolean, extra: Int): OfferNotificationText {
            val a = o.analysis
            val uncertain = o.parserTripped || a.rating == OfferRating.UNCERTAIN
            val verdict = if (uncertain) "? INCERTO" else "${a.rating.symbol} ${a.rating.label}"
            val title = (if (isBest) "★ MELHOR · " else "") + "${o.platform.label} · ${Money.format(a.valueCents)} · $verdict"
            // Calculado direto (arredondado), não derivado do R$/h: R$ 12,81 em 14 min = R$ 0,92/min.
            val perMin = a.centsPerMinute
            val metrics = listOfNotNull(
                a.centsPerKm?.let { "${dot(it, t.acceptableCentsPerKm, t.excellentCentsPerKm, uncertain)} ${Units.perKm(it)}" },
                a.centsPerHour?.let { "${dot(it, t.acceptableCentsPerHour, t.excellentCentsPerHour, uncertain)} ${Money.formatShort(it)}/h" },
                perMin?.let { "${dot(it, t.acceptableCentsPerHour / 60, t.excellentCentsPerHour / 60, uncertain)} ${Money.format(it)}/min" },
            ).joinToString("   ")
            val details = listOfNotNull(
                a.totalMeters?.let { Units.km(it) },
                a.totalSeconds?.let { Units.duration(it) },
                a.profitCents?.let { p ->
                    "lucro ${Money.format(p)}" + (a.netCentsPerHour?.let { " (${Money.formatShort(it)}/h)" } ?: "")
                },
                if (extra > 0) "+$extra outra(s) na tela" else null,
            ).joinToString(" · ")
            return OfferNotificationText(title, metrics, details)
        }

        private fun dot(value: Long, acceptable: Long, excellent: Long, uncertain: Boolean) = when {
            uncertain -> "⚪"
            value >= excellent -> "🟢"
            value >= acceptable -> "🟡"
            else -> "🔴"
        }
    }
}
