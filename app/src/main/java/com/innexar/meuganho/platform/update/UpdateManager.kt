package com.innexar.meuganho.platform.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.innexar.meuganho.BuildConfig
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.data.preferences.SettingsRepository
import com.innexar.meuganho.data.remote.HttpJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Manifesto publicado em innexar.com.br/meuganho/latest.json pelo scripts/publish.sh. */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String = "",
    val publishedAt: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState
    data class Ready(val manifest: UpdateManifest, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Atualização automática fora da Play Store.
 *
 * Verifica no máximo a cada 6 h (ou sob demanda), baixa o APK para o cache,
 * confere o SHA-256 do manifesto e entrega ao instalador do Android. O próprio
 * Android garante que só instala se a assinatura for a mesma do app atual.
 * Só aceita APKs do nosso domínio de distribuição.
 */
class UpdateManager(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    @Volatile private var notifiedVersion = 0

    /** Versão que o usuário adiou no popup ("Depois"); volta a aparecer na próxima abertura do app. */
    val postponedVersion = MutableStateFlow(0)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkIfDue() {
        scope.launch {
            val last = settingsRepository.current().lastUpdateCheckAt
            if (System.currentTimeMillis() - last >= CHECK_INTERVAL_MS) check()
        }
    }

    fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        scope.launch(Dispatchers.IO) {
            _state.value = UpdateState.Checking
            runCatching {
                val manifest = json.decodeFromString(UpdateManifest.serializer(), HttpJson.get("${BuildConfig.DIST_BASE_URL}/latest.json"))
                settingsRepository.update { it.copy(lastUpdateCheckAt = System.currentTimeMillis()) }
                manifest
            }.onSuccess { m ->
                _state.value = if (m.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(m) else UpdateState.UpToDate
                if (m.versionCode > BuildConfig.VERSION_CODE) download()
                if (m.versionCode > BuildConfig.VERSION_CODE && m.versionCode != notifiedVersion) {
                    notifiedVersion = m.versionCode
                    UpdateNotifier.show(appContext, m)
                }
            }.onFailure {
                AppLog.e(LogCategory.SYSTEM, "Falha ao verificar atualização", it)
                _state.value = UpdateState.Failed("Sem conexão com o servidor de atualizações")
            }
        }
    }

    fun download() {
        val manifest = (state.value as? UpdateState.Available)?.manifest ?: return
        scope.launch(Dispatchers.IO) {
            _state.value = UpdateState.Downloading(manifest, 0f)
            runCatching { fetch(manifest) }
                .onSuccess {
                    _state.value = UpdateState.Ready(manifest, it)
                    installIfIdle()
                }
                .onFailure {
                    AppLog.e(LogCategory.SYSTEM, "Falha ao baixar atualização", it)
                    _state.value = UpdateState.Failed(it.message ?: "Falha no download")
                }
        }
    }

    private fun fetch(m: UpdateManifest): File {
        val url = resolve(m.apkUrl)
        require(url.startsWith(BuildConfig.DIST_BASE_URL + "/")) { "Origem do APK não confiável" }
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // Só guardamos o APK mais recente.
        val file = File(dir, "meuganho-${m.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = HttpJson.open(url, "GET")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        total += n
                        if (m.sizeBytes > 0) _state.value = UpdateState.Downloading(m, (total.toFloat() / m.sizeBytes).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(m.sha256, ignoreCase = true)) {
            file.delete()
            error("Arquivo corrompido (SHA-256 não confere)")
        }
        return file
    }

    /** Quem diz se é seguro instalar agora (sem turno aberto). Configurado pelo AppContainer. */
    var canInstallNow: suspend () -> Boolean = { false }

    /**
     * Instala sozinho se o APK está pronto e não há turno aberto: instalar reinicia o app e
     * interrompe a leitura das ofertas. Com turno aberto, fica para quando ele for encerrado.
     */
    fun installIfIdle() {
        val ready = state.value as? UpdateState.Ready ?: return
        scope.launch {
            if (!canInstallNow() || !appContext.packageManager.canRequestPackageInstalls()) return@launch
            runCatching { SessionInstaller.install(appContext, ready.file) }
                .onFailure { AppLog.e(LogCategory.SYSTEM, "Falha ao instalar atualização", it) }
        }
    }

    /** Instalação pedida pelo usuário (botão): mesma sessão; pede a permissão se faltar. */
    fun installNow(context: Context) {
        val ready = state.value as? UpdateState.Ready ?: return
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            context.startActivity(installIntent(ready.file))
            return
        }
        scope.launch { runCatching { SessionInstaller.install(appContext, ready.file) } }
    }

    /**
     * Intent para instalar. Se o Android ainda não permite que o MeuGanho instale
     * apps, abre a tela onde o usuário libera isso (uma única vez).
     */
    fun installIntent(file: File): Intent {
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun resolve(apkUrl: String): String =
        if (apkUrl.startsWith("https://")) apkUrl else "${BuildConfig.DIST_BASE_URL}/${apkUrl.trimStart('/')}"

    private companion object {
        // O manifesto tem ~300 bytes: verificar a cada 30 min custa nada e não deixa versão passar despercebida.
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000L
    }
}
