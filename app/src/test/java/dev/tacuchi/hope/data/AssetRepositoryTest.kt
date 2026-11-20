package dev.tacuchi.hope.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AssetRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: HopeDatabase
    private lateinit var repository: AssetRepository
    private lateinit var assetsDir: File
    private var clock = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HopeDatabase::class.java)
            .build()
        assetsDir = temp.newFolder("assets")
        repository = AssetRepository(database, assetsDir) { clock++ }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun staged(name: String) = temp.newFile(name).apply { writeText("contenido de $name") }

    private fun add(kind: AssetNameKind, file: String = "f-${clock}.bin", frameMs: Long? = null) = runBlocking {
        repository.add(listOf(NewAsset(staged(file), kind, AssetOrigin.WhatsApp, "application/octet-stream", frameMs = frameMs)))
            .single()
    }

    @Test
    fun listsNewestFirstAndFiltersByType() = runBlocking {
        val audio = add(AssetNameKind.AudioFromVideo)
        val image = add(AssetNameKind.Image)
        val voice = add(AssetNameKind.VoiceNote)

        assertEquals(listOf(voice.id, image.id, audio.id), repository.observe(null).first().map { it.id })
        assertEquals(listOf(voice.id, audio.id), repository.observe(AssetType.Audio).first().map { it.id })
        assertEquals(listOf(image.id), repository.observe(AssetType.Image).first().map { it.id })
    }

    @Test
    fun numbersEachKindOnItsOwnAndFramesByTime() {
        assertEquals(1, add(AssetNameKind.AudioFromVideo).number)
        assertEquals(1, add(AssetNameKind.VoiceNote).number)
        assertEquals(2, add(AssetNameKind.AudioFromVideo).number)
        assertEquals(1, add(AssetNameKind.Image).number)

        val frame = add(AssetNameKind.Frame, frameMs = 14_000)
        assertNull(frame.number)
        assertEquals(14_000L, frame.frameMs)
        assertEquals(AssetType.Image, frame.type)
    }

    @Test
    fun movesTheStagedFileIntoPrivateStorage() {
        val source = staged("original.ogg")
        val asset = runBlocking {
            repository.add(listOf(NewAsset(source, AssetNameKind.VoiceNote, AssetOrigin.Shared, "audio/ogg"))).single()
        }

        assertFalse(source.exists())
        val stored = repository.fileOf(asset)
        assertEquals(assetsDir, stored.parentFile)
        assertEquals("contenido de original.ogg", stored.readText())
    }

    @Test
    fun deleteRemovesTheRowAndFreesTheFile() = runBlocking {
        val asset = add(AssetNameKind.Image)
        val stored = repository.fileOf(asset)
        assertTrue(stored.exists())

        repository.delete(asset)

        assertTrue(repository.observe(null).first().isEmpty())
        assertFalse(stored.exists())
        assertEquals(0, assetsDir.listFiles()!!.size)
    }
}
