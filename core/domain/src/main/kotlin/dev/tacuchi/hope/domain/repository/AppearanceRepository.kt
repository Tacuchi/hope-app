package dev.tacuchi.hope.domain.repository

import dev.tacuchi.hope.domain.model.Appearance
import kotlinx.coroutines.flow.StateFlow

/** La apariencia elegida: se lee una vez al abrir y cada cambio se guarda enseguida. */
interface AppearanceRepository {
    /** Ya tiene la selección guardada desde el primer valor. */
    val appearance: StateFlow<Appearance>

    fun save(value: Appearance)
}
