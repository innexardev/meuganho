package com.innexar.meuganho.data.local.entity

import androidx.room.Entity

/**
 * Fila de envio ao banco de estatísticas. Guarda só (tipo, id local): o conteúdo é
 * lido do registro atual na hora do envio, então mudanças posteriores (oferta aceita,
 * corrida revisada) vão junto. A chave composta evita itens repetidos na fila.
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["kind", "localId"])
data class SyncOutboxEntity(
    val kind: String,
    val localId: Long,
    val createdAt: Long,
)
