package com.innexar.meuganho.core.geo

/**
 * Geohash: transforma latitude/longitude numa célula de grade.
 * Precisão 6 ≈ 1,2 km × 0,6 km: suficiente para "região boa/ruim", sem revelar
 * endereço nem trajeto. É o único dado de localização que sai do aparelho.
 */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    const val REGION_PRECISION = 6

    fun encode(latitude: Double, longitude: Double, precision: Int = REGION_PRECISION): String {
        var latMin = -90.0; var latMax = 90.0
        var lonMin = -180.0; var lonMax = 180.0
        val out = StringBuilder(precision)
        var bit = 0; var ch = 0; var even = true
        while (out.length < precision) {
            if (even) {
                val mid = (lonMin + lonMax) / 2
                if (longitude >= mid) { ch = ch or (1 shl (4 - bit)); lonMin = mid } else lonMax = mid
            } else {
                val mid = (latMin + latMax) / 2
                if (latitude >= mid) { ch = ch or (1 shl (4 - bit)); latMin = mid } else latMax = mid
            }
            even = !even
            if (bit < 4) bit++ else { out.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return out.toString()
    }
}
