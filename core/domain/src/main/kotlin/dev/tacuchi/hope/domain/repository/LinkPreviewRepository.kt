package dev.tacuchi.hope.domain.repository

import dev.tacuchi.hope.domain.model.Link
import dev.tacuchi.hope.domain.model.LinkPreview

/** Título y miniatura de un enlace, cuando su red los da sin cuenta. */
interface LinkPreviewRepository {
    /** Nulo sin conexión, si la red no responde o no da vista previa: queda la ficha con la red. */
    suspend fun previewOf(link: Link): LinkPreview?
}
