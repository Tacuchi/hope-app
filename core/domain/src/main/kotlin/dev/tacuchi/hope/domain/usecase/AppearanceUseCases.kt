package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.model.Appearance
import dev.tacuchi.hope.domain.repository.AppearanceRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAppearanceUseCase @Inject constructor(private val repository: AppearanceRepository) {
    operator fun invoke(): StateFlow<Appearance> = repository.appearance
}

class SaveAppearanceUseCase @Inject constructor(private val repository: AppearanceRepository) {
    operator fun invoke(value: Appearance) = repository.save(value)
}
