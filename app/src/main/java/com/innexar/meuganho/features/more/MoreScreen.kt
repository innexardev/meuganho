package com.innexar.meuganho.features.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.BuildConfig
import com.innexar.meuganho.app.appContainer
import com.innexar.meuganho.app.navigation.Routes
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg

private data class Item(val title: String, val subtitle: String, val route: String, val highlight: Boolean = false)

/** Menu agrupado em três blocos (Dinheiro, Carro, App) em vez de dez itens soltos. */
@Composable
fun MoreScreen(navigate: (String) -> Unit) {
    val container = LocalContext.current.appContainer
    val reviews by container.statsRepository.reviewCount.collectAsStateWithLifecycle(initialValue = 0)
    val groups = listOf(
        "Dinheiro" to listOf(
            Item("Revisar", if (reviews > 0) "$reviews a confirmar" else "Nada pendente", Routes.REVIEW, highlight = reviews > 0),
            Item("Ofertas recebidas", "Aceitas e não aceitas", Routes.OFFERS),
            Item("Análises", "Melhores horários, Uber x 99", Routes.ANALYTICS),
            Item("Metas", "Diária, semanal e mensal", Routes.GOALS),
        ),
        "Carro" to listOf(
            Item("Veículo", "Consumo e custos por km", Routes.VEHICLE),
            Item("Abastecimentos", "Consumo real do carro", Routes.FUEL),
            Item("Despesas", "Alimentação, pedágio, lavagem…", Routes.EXPENSES),
        ),
        "App" to listOf(
            Item("Configurações", "Limites, custos, painel, backup", Routes.SETTINGS),
            Item("Permissões", "Acessibilidade, localização, bateria", Routes.PERMISSIONS),
            Item("Diagnóstico", "Estado da leitura Uber/99", Routes.DIAGNOSTICS),
        ),
    )
    MgScreen("Mais", onBack = null) {
        groups.forEach { (title, items) ->
            SectionTitle(title)
            MgCard(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = Mg.colors.outline)
                    Row(
                        Modifier.fillMaxWidth().clickable { navigate(item.route) }.padding(vertical = Mg.spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = Mg.type.body, color = Mg.colors.textPrimary)
                            Text(item.subtitle, style = Mg.type.caption,
                                color = if (item.highlight) Mg.colors.warning else Mg.colors.textSecondary)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Mg.colors.textSecondary)
                    }
                }
            }
        }
        Text("MeuGanho ${BuildConfig.VERSION_NAME}", style = Mg.type.caption, color = Mg.colors.textSecondary,
            modifier = Modifier.padding(vertical = Mg.spacing.l))
    }
}
