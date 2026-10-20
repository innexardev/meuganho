package com.innexar.meuganho.features.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.time.DateRange
import com.innexar.meuganho.data.local.entity.ExpenseEntity
import com.innexar.meuganho.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpensesViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.expenseDao()
    private val month = DateRange.thisMonth()

    val expenses: StateFlow<List<ExpenseEntity>> = dao.observeBetween(month.startMillis, month.endMillis)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(category: ExpenseCategory, amount: String, note: String, onError: (String) -> Unit, onSaved: () -> Unit) {
        val cents = Money.parseInput(amount)?.takeIf { it > 0 } ?: return onError("Informe o valor")
        viewModelScope.launch {
            dao.insert(ExpenseEntity(at = System.currentTimeMillis(), shiftId = container.database.shiftDao().open()?.id,
                category = category, amountCents = cents, note = note.ifBlank { null }))
            onSaved()
        }
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}
