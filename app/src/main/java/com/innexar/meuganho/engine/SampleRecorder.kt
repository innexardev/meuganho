package com.innexar.meuganho.engine

import com.innexar.meuganho.capture.core.ParserRegistry
import com.innexar.meuganho.capture.core.SampleReason
import com.innexar.meuganho.capture.core.SamplingPolicy
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.core.SnapshotSanitizer
import com.innexar.meuganho.data.local.dao.DiagnosticSampleDao
import com.innexar.meuganho.data.local.entity.DiagnosticSampleEntity
import com.innexar.meuganho.data.remote.SampleUploader
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.domain.model.Platform
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Gravação automática de telas para calibração. A política decide o que vale
 * guardar; o texto é sanitizado ANTES de tocar o disco. O envio é do [SampleUploader].
 */
class SampleRecorder(
    private val dao: DiagnosticSampleDao,
    private val uploader: SampleUploader,
    private val policy: SamplingPolicy = SamplingPolicy(),
) {
    suspend fun record(
        platform: Platform,
        type: ScreenType,
        reason: SampleReason,
        snapshot: ScreenSnapshot,
        parserVersion: String,
        parse: JsonObject,
    ) {
        // Telas sem texto (99) diferem só no censo de janelas: ele entra na identidade da amostra.
        val identity = snapshot.contentHash * 31 + snapshot.debug.substringAfter("census=", "").hashCode()
        if (!policy.shouldSample(platform, type, reason, identity, snapshot.capturedAt)) return
        val channelSuffix = if (snapshot.channel == CaptureChannel.ACCESSIBILITY) "" else "_${snapshot.channel.name}"
        dao.insert(
            DiagnosticSampleEntity(
                capturedAt = snapshot.capturedAt,
                platform = platform,
                screenType = type,
                reason = reason.name + channelSuffix,
                parserVersion = parserVersion,
                sourceAppVersion = snapshot.appVersionCode,
                sanitizedText = SnapshotSanitizer.sanitize(snapshot, ParserRegistry.sanitizerKeywords(platform)),
                // A radiografia da árvore vai junto: prova se o app esconde o texto ou se nós cortamos a leitura.
                parseSummary = JsonObject(parse + ("tree" to JsonPrimitive(snapshot.debug))).toString(),
            )
        )
        uploader.maybeUpload(urgent = reason != SampleReason.SCREEN)
    }
}
