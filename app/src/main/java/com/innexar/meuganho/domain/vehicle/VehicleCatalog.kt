package com.innexar.meuganho.domain.vehicle

import com.innexar.meuganho.domain.model.FuelType

/** Porte do carro: define a sugestão de custo de manutenção e depreciação por km. */
enum class CarClass(val label: String, val maintenanceCentsPerKm: Double, val depreciationCentsPerKm: Double) {
    POPULAR("Popular", 10.0, 12.0),
    SEDAN("Sedã", 12.0, 15.0),
    SUV("SUV / minivan", 15.0, 20.0),
    HYBRID("Híbrido", 13.0, 22.0),
    ELECTRIC("Elétrico", 5.0, 25.0),
}

/**
 * Versão de catálogo. Consumos: gasolina (ou km/kWh para elétricos) e etanol,
 * cidade/estrada. São ESTIMATIVAS próximas do PBE/Inmetro para uso misto; o app
 * as mostra como estimadas e os abastecimentos registrados corrigem com o tempo.
 */
data class CatalogCar(
    val manufacturer: String,
    val model: String,
    val version: String,
    val fuel: FuelType,
    val city: Double,
    val highway: Double,
    val ethanolCity: Double? = null,
    val ethanolHighway: Double? = null,
    val carClass: CarClass,
) {
    val label: String get() = "$model $version"
}

object VehicleCatalog {
    private fun flex(m: String, model: String, v: String, gc: Double, gh: Double, ec: Double, eh: Double, c: CarClass) =
        CatalogCar(m, model, v, FuelType.FLEX, gc, gh, ec, eh, c)
    private fun ev(m: String, model: String, v: String, c: Double, h: Double) =
        CatalogCar(m, model, v, FuelType.ELECTRIC, c, h, carClass = CarClass.ELECTRIC)
    private fun gas(m: String, model: String, v: String, c: Double, h: Double, cls: CarClass) =
        CatalogCar(m, model, v, FuelType.GASOLINE, c, h, carClass = cls)

    val cars: List<CatalogCar> = listOf(
        // Populares de aplicativo
        flex("Chevrolet", "Onix", "1.0", 13.0, 15.5, 9.1, 10.8, CarClass.POPULAR),
        flex("Chevrolet", "Onix", "1.0 Turbo", 12.1, 15.3, 8.6, 10.7, CarClass.POPULAR),
        flex("Chevrolet", "Onix Plus", "1.0 Turbo", 12.3, 15.7, 8.7, 11.0, CarClass.SEDAN),
        flex("Chevrolet", "Prisma", "1.4", 12.1, 14.2, 8.5, 10.0, CarClass.SEDAN),
        flex("Chevrolet", "Cobalt", "1.8", 10.2, 12.1, 7.2, 8.5, CarClass.SEDAN),
        flex("Chevrolet", "Spin", "1.8", 9.9, 11.3, 7.0, 8.0, CarClass.SUV),
        flex("Chevrolet", "Tracker", "1.0 Turbo", 11.2, 13.4, 7.9, 9.5, CarClass.SUV),
        flex("Fiat", "Mobi", "1.0", 13.5, 15.1, 9.5, 10.6, CarClass.POPULAR),
        flex("Fiat", "Argo", "1.0", 13.1, 14.6, 9.2, 10.3, CarClass.POPULAR),
        flex("Fiat", "Argo", "1.3", 12.4, 13.8, 8.6, 9.7, CarClass.POPULAR),
        flex("Fiat", "Cronos", "1.3", 12.3, 14.3, 8.6, 10.0, CarClass.SEDAN),
        flex("Fiat", "Grand Siena", "1.4", 11.3, 13.2, 7.9, 9.2, CarClass.SEDAN),
        flex("Fiat", "Pulse", "1.3", 11.9, 13.1, 8.3, 9.3, CarClass.SUV),
        flex("Volkswagen", "Gol", "1.0", 12.9, 14.5, 8.9, 10.2, CarClass.POPULAR),
        flex("Volkswagen", "Voyage", "1.6", 11.1, 13.0, 7.8, 9.1, CarClass.SEDAN),
        flex("Volkswagen", "Polo", "1.0", 13.2, 15.1, 9.3, 10.6, CarClass.POPULAR),
        flex("Volkswagen", "Polo", "1.0 TSI", 12.4, 14.7, 8.6, 10.3, CarClass.POPULAR),
        flex("Volkswagen", "Virtus", "1.0 TSI", 12.3, 14.8, 8.6, 10.4, CarClass.SEDAN),
        flex("Volkswagen", "T-Cross", "1.0 TSI", 11.1, 13.0, 7.7, 9.1, CarClass.SUV),
        flex("Hyundai", "HB20", "1.0", 13.4, 15.1, 9.3, 10.6, CarClass.POPULAR),
        flex("Hyundai", "HB20", "1.0 Turbo", 12.4, 14.4, 8.7, 10.0, CarClass.POPULAR),
        flex("Hyundai", "HB20S", "1.0 Turbo", 12.3, 14.8, 8.6, 10.3, CarClass.SEDAN),
        flex("Hyundai", "Creta", "1.0 Turbo", 11.3, 13.1, 7.9, 9.2, CarClass.SUV),
        flex("Renault", "Kwid", "1.0", 14.9, 15.6, 10.3, 10.8, CarClass.POPULAR),
        flex("Renault", "Sandero", "1.0", 12.9, 14.2, 9.0, 9.9, CarClass.POPULAR),
        flex("Renault", "Logan", "1.0", 12.8, 14.3, 9.0, 10.0, CarClass.SEDAN),
        flex("Renault", "Logan", "1.6", 11.2, 12.9, 7.8, 9.0, CarClass.SEDAN),
        ev("Renault", "Kwid E-Tech", "Elétrico", 8.5, 7.5),
        flex("Toyota", "Etios", "1.5", 12.0, 14.0, 8.4, 9.8, CarClass.SEDAN),
        flex("Toyota", "Yaris", "1.5", 12.1, 14.3, 8.5, 10.0, CarClass.SEDAN),
        flex("Toyota", "Corolla", "2.0", 11.0, 13.3, 7.6, 9.3, CarClass.SEDAN),
        CatalogCar("Toyota", "Corolla", "1.8 Híbrido", FuelType.FLEX, 17.9, 15.5, 12.4, 11.0, CarClass.HYBRID),
        CatalogCar("Toyota", "Corolla Cross", "1.8 Híbrido", FuelType.FLEX, 17.8, 15.0, 12.3, 10.6, CarClass.HYBRID),
        flex("Nissan", "Versa", "1.6", 12.4, 14.7, 8.6, 10.2, CarClass.SEDAN),
        flex("Nissan", "Kicks", "1.6", 11.4, 13.5, 8.0, 9.4, CarClass.SUV),
        flex("Honda", "City", "1.5", 13.1, 14.9, 9.1, 10.6, CarClass.SEDAN),
        flex("Honda", "Fit", "1.5", 12.9, 14.8, 9.0, 10.4, CarClass.POPULAR),
        flex("Peugeot", "208", "1.0", 13.4, 15.3, 9.4, 10.7, CarClass.POPULAR),
        flex("Citroën", "C3", "1.0", 13.4, 15.1, 9.4, 10.6, CarClass.POPULAR),
        flex("Citroën", "C3 Aircross", "1.0 Turbo", 11.2, 13.0, 7.8, 9.1, CarClass.SUV),
        // Chinesas
        ev("BYD", "Dolphin Mini", "Elétrico", 8.9, 7.7),
        ev("BYD", "Dolphin", "Elétrico", 7.6, 6.4),
        ev("BYD", "Seal", "Elétrico", 6.8, 5.9),
        ev("BYD", "Yuan Plus", "Elétrico", 6.4, 5.6),
        gas("BYD", "Song Plus", "DM-i (híbrido plug-in)", 15.0, 14.0, CarClass.HYBRID),
        gas("BYD", "King", "DM-i (híbrido plug-in)", 17.0, 16.0, CarClass.HYBRID),
        gas("GWM", "Haval H6", "HEV (híbrido)", 14.4, 12.9, CarClass.HYBRID),
        ev("GWM", "Ora 03", "Elétrico", 7.2, 6.1),
        flex("Caoa Chery", "Tiggo 5x Pro", "1.5 Turbo", 10.8, 12.6, 7.5, 8.8, CarClass.SUV),
        flex("Caoa Chery", "Tiggo 7 Pro", "1.5 Turbo", 10.1, 12.0, 7.1, 8.4, CarClass.SUV),
        flex("Caoa Chery", "Arrizo 6 Pro", "1.5 Turbo", 11.3, 13.8, 7.9, 9.7, CarClass.SEDAN),
        gas("Omoda", "Omoda 5", "1.5 Turbo", 10.9, 12.5, CarClass.SUV),
        ev("JAC", "E-JS1", "Elétrico", 8.6, 7.4),
        ev("GAC", "Aion Y", "Elétrico", 7.2, 6.3),
        ev("Leapmotor", "C10", "Elétrico", 6.5, 5.6),
    )

    val manufacturers: List<String> = cars.map { it.manufacturer }.distinct().sorted()

    fun models(manufacturer: String): List<CatalogCar> = cars.filter { it.manufacturer == manufacturer }

    /** Consumo médio (60% cidade, 40% estrada): perfil típico de aplicativo. */
    fun mixed(city: Double, highway: Double): Double = city * 0.6 + highway * 0.4

    /** Motorizações comuns para o campo Versão (quando o modelo não está no catálogo). */
    val commonVersions = listOf("1.0", "1.0 Turbo", "1.2", "1.3", "1.4", "1.5", "1.6", "1.8", "2.0", "Híbrido", "Elétrico")
}
