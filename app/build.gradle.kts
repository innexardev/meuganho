import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.innexar.meuganho"
    compileSdk = 36

    // Chave de release fora do repositório: keystore.properties (symlink para /srv/innexar/secrets).
    // Sem ela o release não é assinado; nunca assinar distribuição com a chave de debug,
    // porque trocar de chave depois obriga a desinstalar o app (e perder o histórico).
    val keystoreFile = rootProject.file("keystore.properties")
    signingConfigs {
        if (keystoreFile.exists()) {
            val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.innexar.meuganho"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "0.6.5"

        // Distribuição própria (innexar.com.br/meuganho): atualizações e amostras de calibração.
        buildConfigField("String", "DIST_BASE_URL", "\"https://innexar.com.br/meuganho\"")
        // Chave anti-spam do envio de amostras. Vai dentro do APK: não protege nada sensível.
        val uploadKey = if (keystoreFile.exists()) {
            Properties().apply { keystoreFile.inputStream().use { load(it) } }.getProperty("uploadKey", "")
        } else {
            ""
        }
        buildConfigField("String", "UPLOAD_KEY", "\"$uploadKey\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Recursos reais nos testes: as capturas de tela usam tema, ícones e strings do app.
        unitTests.isIncludeAndroidResources = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

room { schemaDirectory("$projectDir/schemas") }

// Capturas de tela (Robolectric) são lentas: a publicação usa -PskipScreens e roda só as regras.
tasks.withType<Test>().configureEach {
    if (project.hasProperty("skipScreens")) filter { excludeTestsMatching("*ScreensTest*") }
    // Simulação de turnos: -Psim.shifts=N (padrão 20) e -Psim.out=pasta do relatório.
    project.findProperty("sim.shifts")?.let { systemProperty("sim.shifts", it) }
    project.findProperty("sim.out")?.let { systemProperty("sim.out", it) }
    outputs.upToDateWhen { false }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // OCR no aparelho via Google Play Services (modelo baixado pelo sistema; APK continua ~2 MB).
    // A versão com modelo embutido deixava o APK com 43 MB. Fallback só para a 99.
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Capturas das telas no servidor (Robolectric + captura nativa do Compose), sem emulador.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
