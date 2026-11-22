package dev.tacuchi.hope.domain.testing

import dev.tacuchi.hope.domain.media.MediaInspector
import dev.tacuchi.hope.domain.model.MediaInspection
import java.io.File

/** Dice qué es cada archivo sin abrirlo: lo que la prueba fije en [next]. */
class FakeMediaInspector(
    var next: MediaInspection = MediaInspection(isImage = false, hasVideo = false, hasAudio = true, durationMs = 5_000),
) : MediaInspector {
    override suspend fun inspect(file: File) = next
}
