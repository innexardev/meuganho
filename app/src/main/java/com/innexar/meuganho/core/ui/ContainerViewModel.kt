package com.innexar.meuganho.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.app.appContainer

/** Cria um ViewModel recebendo o [AppContainer], sem framework de DI. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(key = key) { create(container) }
}
