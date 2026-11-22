package dev.tacuchi.hope.ui.importing

import android.net.Uri
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.media.AudioExtractor
import dev.tacuchi.hope.domain.media.FrameGrabber
import dev.tacuchi.hope.domain.media.FrameReader
import dev.tacuchi.hope.domain.media.ImportStaging
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.MediaInspection
import dev.tacuchi.hope.domain.model.StagedFile
import dev.tacuchi.hope.domain.testing.FakeAssetRepository
import dev.tacuchi.hope.domain.testing.FakeMediaInspector
import dev.tacuchi.hope.domain.usecase.CancelImportUseCase
import dev.tacuchi.hope.domain.usecase.CloseFramesUseCase
import dev.tacuchi.hope.domain.usecase.ConfirmImportedFileUseCase
import dev.tacuchi.hope.domain.usecase.ConfirmVideoImportUseCase
import dev.tacuchi.hope.domain.usecase.DiscardImportLeftoversUseCase
import dev.tacuchi.hope.domain.usecase.ExtractAudioUseCase
import dev.tacuchi.hope.domain.usecase.GetFramePreviewUseCase
import dev.tacuchi.hope.domain.usecase.OpenFramesUseCase
import dev.tacuchi.hope.domain.usecase.PrepareImportUseCase
import dev.tacuchi.hope.domain.usecase.StageGalleryImageUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID

/** La carpeta temporal de la importación sobre archivos locales: copia lo que señala una URI `file:`. */
private class FakeImportStaging(private val root: File) : ImportStaging {
    override fun newSession() = File(root, UUID.randomUUID().toString()).apply { mkdirs() }

    override suspend fun copy(uri: String, session: File, declaredMime: String?): StagedFile {
        val source = File(URI(uri))
        val target = File(session, "original.${source.extension}")
        source.copyTo(target)
        val mime = declaredMime?.takeUnless { it.endsWith("/*") }
        return StagedFile(target, mime, source.name, mime ?: "application/octet-stream")
    }

    override fun newSubfolder(session: File, name: String) = File(session, "$name-${UUID.randomUUID()}").apply { mkdirs() }

    override fun discard(session: File) {
        session.deleteRecursively()
    }

    override fun discardAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }
}

/** Extrae sin Media3: escribe bytes de mentira o falla. Con [gate] espera hasta que se complete. */
private class FakeExtractor : AudioExtractor {
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun extract(input: File, output: File, onProgress: (Int?) -> Unit): Long {
        onProgress(50)
        gate?.await()
        failure?.let { throw it }
        output.writeBytes(ByteArray(32) { 2 })
        return 20_000
    }
}

/** Un video de 20 s cuyos fotogramas son archivos de mentira. */
private class FakeFrameGrabber : FrameGrabber {
    override fun open(video: File, previewDir: File) = object : FrameReader {
        override val durationMs = 20_000L
        override suspend fun preview(positionMs: Long, maxSide: Int) =
            File(previewDir, "preview-$positionMs-$maxSide.jpg").apply { writeBytes(ByteArray(8)) }
        override suspend fun save(positionMs: Long, target: File): Boolean {
            target.writeBytes(ByteArray(16))
            return true
        }
        override fun close() = Unit
    }
}

@RunWith(AndroidJUnit4::class)
class ImportViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var repository: FakeAssetRepository
    private lateinit var stagingRoot: File
    private val inspector = FakeMediaInspector()
    private val extractor = FakeExtractor()
    private lateinit var vm: ImportViewModel

    @Before
    fun setUp() {
        repository = FakeAssetRepository(temp.newFolder("assets"))
        stagingRoot = temp.newFolder("import-tmp")
        // Restos de una importación que el sistema cortó: el ViewModel los borra al crearse.
        File(stagingRoot, "sesion-anterior").mkdirs()
        val staging = FakeImportStaging(stagingRoot)
        val grabber = FakeFrameGrabber()
        vm = ImportViewModel(
            DiscardImportLeftoversUseCase(staging),
            PrepareImportUseCase(staging, inspector),
            ExtractAudioUseCase(extractor),
            OpenFramesUseCase(grabber, staging),
            GetFramePreviewUseCase(),
            CloseFramesUseCase(),
            StageGalleryImageUseCase(staging, inspector),
            ConfirmImportedFileUseCase(repository),
            ConfirmVideoImportUseCase(repository),
            CancelImportUseCase(staging),
        )
        waitUntil { stagingIsEmpty() }
    }

    /** Un archivo recibido, como lo daría otra app. */
    private fun received(name: String): Uri = Uri.fromFile(temp.newFile(name).apply { writeBytes(ByteArray(64) { 1 }) })

    private fun savedAssets() = runBlocking { repository.observe(null).first() }

    private fun stagingIsEmpty() = stagingRoot.listFiles().isNullOrEmpty()

    /** El ViewModel trabaja en corrutinas que vuelven al hilo principal: se procesa hasta que se cumpla. */
    private fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        error("No se cumplió a tiempo")
    }

    private fun finished(): ImportState.Finished {
        waitUntil { vm.state.value is ImportState.Finished }
        return vm.state.value as ImportState.Finished
    }

    @Test
    fun anAudioFromAssetsGoesBackToAssets() {
        vm.start(received("cancion.mp3"), declaredMime = null, origin = AssetOrigin.Files)

        val result = finished()

        assertEquals(ImportPurpose.Catalog, result.purpose)
        assertEquals(AssetNameKind.Audio, result.assets.single().nameKind)
        assertEquals(5_000L, result.assets.single().durationMs)
        assertEquals(result.assets, savedAssets())
        assertTrue(stagingIsEmpty())

        vm.acknowledgeFinished()
        assertEquals(ImportState.Idle, vm.state.value)
    }

    @Test
    fun aSharedImageGoesToANewHope() {
        inspector.next = MediaInspection(isImage = true, hasVideo = false, hasAudio = false, durationMs = null)

        vm.start(received("foto.png"), declaredMime = "image/png", origin = AssetOrigin.WhatsApp, purpose = ImportPurpose.Share)

        val result = finished()
        assertEquals(ImportPurpose.Share, result.purpose)
        assertEquals(AssetNameKind.Image, result.assets.single().nameKind)
        assertEquals(AssetOrigin.WhatsApp, result.assets.single().origin)
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun anImportFromTheEditorReturnsToTheSlotThatAsked() {
        vm.start(received("nota.ogg"), declaredMime = null, origin = AssetOrigin.Files, purpose = ImportPurpose.EditorBackground)

        val result = finished()
        assertEquals(ImportPurpose.EditorBackground, result.purpose)
        assertEquals(AssetNameKind.VoiceNote, result.assets.single().nameKind)
    }

    @Test
    fun anUnsupportedFileIsReportedAndCancellingLeavesNothing() {
        inspector.next = MediaInspection(isImage = false, hasVideo = false, hasAudio = false, durationMs = null)

        vm.start(received("documento.pdf"), declaredMime = "application/pdf", origin = AssetOrigin.Files)
        waitUntil { vm.state.value is ImportState.Unsupported }
        assertEquals(ImportState.Unsupported("PDF (application/pdf)"), vm.state.value)

        vm.cancel()

        assertEquals(ImportState.Idle, vm.state.value)
        assertTrue(stagingIsEmpty())
        assertTrue(savedAssets().isEmpty())
    }

    @Test
    fun aFailedExtractionCreatesNothingAndCleansUp() {
        inspector.next = MediaInspection(isImage = false, hasVideo = true, hasAudio = true, durationMs = 20_000)
        extractor.failure = IOException("códec raro")

        vm.start(received("video.mp4"), declaredMime = null, origin = AssetOrigin.TikTok, purpose = ImportPurpose.Share)
        waitUntil { vm.state.value == ImportState.Failed }

        assertTrue(stagingIsEmpty())
        assertTrue(savedAssets().isEmpty())
    }

    @Test
    fun cancellingWhileExtractingCreatesNothingAndCleansUp() {
        inspector.next = MediaInspection(isImage = false, hasVideo = true, hasAudio = true, durationMs = 20_000)
        extractor.gate = CompletableDeferred()

        vm.start(received("video.mp4"), declaredMime = null, origin = AssetOrigin.TikTok)
        waitUntil { vm.state.value == ImportState.Extracting(50) }
        vm.cancel()
        waitUntil { stagingIsEmpty() }

        assertEquals(ImportState.Idle, vm.state.value)
        assertTrue(savedAssets().isEmpty())
    }

    @Test
    fun cancellingTheCoverKeepsTheExtractedAudio() {
        inspector.next = MediaInspection(isImage = false, hasVideo = true, hasAudio = true, durationMs = 20_000)

        vm.start(received("video.mp4"), declaredMime = null, origin = AssetOrigin.TikTok, purpose = ImportPurpose.Share)
        waitUntil { vm.state.value is ImportState.ChoosingCover }
        vm.cancel()

        val result = finished()
        assertEquals(ImportPurpose.Share, result.purpose)
        val audio = result.assets.single()
        assertEquals(AssetNameKind.AudioFromVideo, audio.nameKind)
        assertEquals(AssetOrigin.TikTok, audio.origin)
        assertEquals(20_000L, audio.durationMs)
        assertEquals(listOf(audio), savedAssets())
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun usingTheChosenFrameSavesTheExtractedAudioAndTheCover() {
        inspector.next = MediaInspection(isImage = false, hasVideo = true, hasAudio = true, durationMs = 20_000)

        vm.start(received("video.mp4"), declaredMime = null, origin = AssetOrigin.TikTok, purpose = ImportPurpose.Share)
        waitUntil { (vm.state.value as? ImportState.ChoosingCover)?.suggestions?.all { it.thumbnail != null } == true }
        vm.selectPosition(6_000)
        vm.confirmCover()

        val result = finished()
        assertEquals(listOf(AssetNameKind.AudioFromVideo, AssetNameKind.Frame), result.assets.map { it.nameKind })
        assertEquals(6_000L, result.assets.last().frameMs)
        assertEquals(result.assets.toSet(), savedAssets().toSet())
        assertTrue(stagingIsEmpty())
    }
}
