package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.model.Link
import dev.tacuchi.hope.domain.model.LinkPreview
import dev.tacuchi.hope.domain.model.Links
import dev.tacuchi.hope.domain.repository.LinkPreviewRepository
import javax.inject.Inject

/** Lo escrito como enlace de origen, reconocido con su red; nulo si no es un enlace. */
class RecognizeLinkUseCase @Inject constructor() {
    operator fun invoke(text: String): Link? = Links.recognize(text)
}

class GetLinkPreviewUseCase @Inject constructor(private val previews: LinkPreviewRepository) {
    suspend operator fun invoke(link: Link): LinkPreview? = previews.previewOf(link)
}
