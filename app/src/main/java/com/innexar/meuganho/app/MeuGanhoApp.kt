package com.innexar.meuganho.app

import android.app.Application
import com.innexar.meuganho.platform.notifications.NotificationChannels

class MeuGanhoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.innexar.meuganho.core.crash.CrashRecorder.install(this)
        container = AppContainer(this)
        NotificationChannels.register(this)
        container.onAppStart()
    }
}

/** Atalho para serviços e Activity: `context.appContainer`. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as MeuGanhoApp).container
