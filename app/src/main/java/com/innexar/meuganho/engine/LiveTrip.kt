package com.innexar.meuganho.engine

import com.innexar.meuganho.domain.model.DriveSegment
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState

/** Estado ao vivo da corrida, lido pelo GPS (segmento) e pelo overlay. */
data class LiveTrip(
    val state: TripState = TripState.IDLE,
    val platform: Platform? = null,
    val tripId: Long? = null,
    val segment: DriveSegment = DriveSegment.EMPTY,
)
