package dev.tacuchi.hope.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.links.LinkNetwork
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HopeRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: HopeDatabase
    private lateinit var assets: AssetRepository
    private lateinit var hopes: HopeRepository
    private lateinit var assetsDir: File
    private var clock = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HopeDatabase::class.java)
            .build()
        assetsDir = temp.newFolder("assets")
        assets = AssetRepository(database, assetsDir) { clock++ }
        hopes = HopeRepository(database) { clock++ }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun add(kind: AssetNameKind, durationMs: Long? = null) = runBlocking {
        val file = temp.newFile("f-${clock++}.bin").apply { writeText("datos") }
        assets.add(listOf(NewAsset(file, kind, AssetOrigin.WhatsApp, "application/octet-stream", durationMs = durationMs))).single()
    }

    private fun hope(title: String, cover: Asset? = null, background: Asset? = null, voice: Asset? = null) = Hope(
        title = title,
        coverAssetId = cover?.id,
        backgroundAssetId = background?.id,
        voiceAssetId = voice?.id,
        backgroundVolume = 30,
        voiceVolume = 100,
        trimStartMs = 0,
        trimEndMs = null,
        voiceOffsetMs = 2_000,
        createdAt = 0,
    )

    @Test
    fun savedHopesListNewestFirstWithTheirAssetsAndDuration() = runBlocking {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val voice = add(AssetNameKind.Recording, durationMs = 41_000)
        val cover = add(AssetNameKind.Frame)
        val older = hopes.save(hope("Respira", background = background))
        val newer = hopes.save(hope("Antes de entrar", cover, background, voice).copy(trimStartMs = 4_000, trimEndMs = 48_000))

        val listed = hopes.observe().first()

        assertEquals(listOf(newer, older), listed.map { it.hope.id })
        val first = listed.first()
        assertEquals(cover, first.cover)
        assertEquals(voice, first.voice)
        // Fondo recortado: 44 s. Voz: entra a los 2 s y dura 41 s, termina a los 43 s.
        assertEquals(44_000L, first.durationMs)
    }

    @Test
    fun editingKeepsTheIdAndReplacesTheSettings() = runBlocking {
        val background = add(AssetNameKind.Audio, durationMs = 10_000)
        val id = hopes.save(hope("Lunes", background = background))
        val saved = hopes.get(id)!!.hope

        hopes.save(saved.copy(title = "Lunes otra vez", backgroundVolume = 60))

        val edited = hopes.get(id)!!.hope
        assertEquals("Lunes otra vez", edited.title)
        assertEquals(60, edited.backgroundVolume)
        assertEquals(saved.createdAt, edited.createdAt)
        assertEquals(1, hopes.observe().first().size)
    }

    @Test
    fun deletingAHopeKeepsItsAssetsAndTheirFiles() = runBlocking {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 10_000)
        val cover = add(AssetNameKind.Image)
        val id = hopes.save(hope("Para el examen", cover, background))

        hopes.delete(id)

        assertTrue(hopes.observe().first().isEmpty())
        assertEquals(setOf(background.id, cover.id), assets.observe(null).first().map { it.id }.toSet())
        assertTrue(assets.fileOf(background).exists())
        assertTrue(assets.fileOf(cover).exists())
    }

    @Test
    fun anAssetInUseCountsItsHopesAndIsNotDeleted() = runBlocking {
        val audio = add(AssetNameKind.AudioFromVideo, durationMs = 10_000)
        val unused = add(AssetNameKind.Image)
        val first = hopes.save(hope("Respira", background = audio))
        val second = hopes.save(hope("Camino a casa", background = audio))

        assertEquals(listOf(second, first), hopes.observeUses().first()[audio.id]!!.map { it.hopeId })
        assertEquals(null, hopes.observeUses().first()[unused.id])

        val result = assets.delete(audio)

        assertEquals(listOf("Camino a casa", "Respira"), (result as AssetDeletion.InUse).hopes.map { it.title })
        assertTrue(assets.fileOf(audio).exists())
        assertEquals(2, assets.observe(null).first().size)
        assertEquals(AssetDeletion.Deleted, assets.delete(unused))
    }

    @Test
    fun anAssetIsFreeAgainOnceNoHopeUsesIt() = runBlocking {
        val audio = add(AssetNameKind.AudioFromVideo, durationMs = 10_000)
        val id = hopes.save(hope("Respira", background = audio))
        hopes.delete(id)

        assertEquals(AssetDeletion.Deleted, assets.delete(audio))
        assertTrue(assets.observe(null).first().isEmpty())
    }

    @Test
    fun usingAnAssetInAnExistingHopePutsItInItsSlot() = runBlocking {
        val first = add(AssetNameKind.AudioFromVideo, durationMs = 10_000)
        val other = add(AssetNameKind.Audio, durationMs = 20_000)
        val image = add(AssetNameKind.Image)
        val id = hopes.save(hope("Respira", background = first).copy(trimStartMs = 3_000))

        hopes.useAsset(id, other)
        hopes.useAsset(id, image)

        val updated = hopes.get(id)!!
        assertEquals(other, updated.background)
        assertEquals(image, updated.cover)
        assertEquals(0L, updated.hope.trimStartMs)
    }

    /** Una base creada por la versión anterior de la app (solo assets) abre con sus assets intactos. */
    @Test
    fun migrationFromTheFirstVersionKeepsTheAssets() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migracion.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL(
                "CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                    "`nameKind` TEXT NOT NULL, `number` INTEGER, `frameMs` INTEGER, `origin` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `durationMs` INTEGER, `fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL)",
            )
            old.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_type_createdAt` ON `assets` (`type`, `createdAt`)")
            old.execSQL(
                "INSERT INTO assets (type, nameKind, number, frameMs, origin, createdAt, durationMs, fileName, mimeType) " +
                    "VALUES ('Audio', 'VoiceNote', 1, NULL, 'WhatsApp', 10, 3000, 'a.ogg', 'audio/ogg')",
            )
            old.execSQL(
                "INSERT INTO assets (type, nameKind, number, frameMs, origin, createdAt, durationMs, fileName, mimeType) " +
                    "VALUES ('Image', 'Frame', NULL, 14000, 'TikTok', 20, NULL, 'f.jpg', 'image/jpeg')",
            )
            // La versión 1 llamaba «Nota de voz» a todo audio importado, también a un MP3.
            old.execSQL(
                "INSERT INTO assets (type, nameKind, number, frameMs, origin, createdAt, durationMs, fileName, mimeType) " +
                    "VALUES ('Audio', 'VoiceNote', 2, NULL, 'Files', 30, 8000, 'c.mp3', 'audio/mpeg')",
            )
            old.execSQL(
                "INSERT INTO assets (type, nameKind, number, frameMs, origin, createdAt, durationMs, fileName, mimeType) " +
                    "VALUES ('Audio', 'VoiceNote', 3, NULL, 'Files', 40, 6000, 'n.ogg', 'audio/ogg')",
            )
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '088ea1821c8ac303305ecba159f5b656')")
            old.version = 1
        }

        database = HopeDatabase.create(context, "migracion.db")
        val migratedAssets = AssetRepository(database, assetsDir)
        val migratedHopes = HopeRepository(database)

        val kept = migratedAssets.observe(null).first()
        assertEquals(listOf("n.ogg", "c.mp3", "f.jpg", "a.ogg"), kept.map { it.fileName })
        // Solo los OGG siguen como «Nota de voz», numerados por fecha; el MP3 pasa a «Audio 1».
        assertEquals(
            listOf(AssetNameKind.VoiceNote to 2, AssetNameKind.Audio to 1, AssetNameKind.Frame to null, AssetNameKind.VoiceNote to 1),
            kept.map { it.nameKind to it.number },
        )
        val id = migratedHopes.save(hope("Después de migrar", background = kept.last()))
        assertNotNull(migratedHopes.get(id))
    }

    /** Una base de la versión 2 (assets y hopes) abre con todo intacto y las hopes sin enlace. */
    @Test
    fun migrationFromTheSecondVersionKeepsHopesAndAssets() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migracion-2.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            // Tablas tal como las exporta app/schemas/…/2.json.
            old.execSQL(
                "CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                    "`nameKind` TEXT NOT NULL, `number` INTEGER, `frameMs` INTEGER, `origin` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `durationMs` INTEGER, `fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL)",
            )
            old.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_type_createdAt` ON `assets` (`type`, `createdAt`)")
            old.execSQL(
                "CREATE TABLE IF NOT EXISTS `hopes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`coverAssetId` INTEGER, `backgroundAssetId` INTEGER, `voiceAssetId` INTEGER, `backgroundVolume` INTEGER NOT NULL, " +
                    "`voiceVolume` INTEGER NOT NULL, `trimStartMs` INTEGER NOT NULL, `trimEndMs` INTEGER, `voiceOffsetMs` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`coverAssetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`backgroundAssetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`voiceAssetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            listOf("coverAssetId", "backgroundAssetId", "voiceAssetId", "createdAt").forEach { column ->
                old.execSQL("CREATE INDEX IF NOT EXISTS `index_hopes_$column` ON `hopes` (`$column`)")
            }
            old.execSQL(
                "INSERT INTO assets (type, nameKind, number, frameMs, origin, createdAt, durationMs, fileName, mimeType) " +
                    "VALUES ('Audio', 'AudioFromVideo', 1, NULL, 'TikTok', 10, 52000, 'a.m4a', 'audio/mp4')",
            )
            old.execSQL(
                "INSERT INTO assets (type, nameKind, number, frameMs, origin, createdAt, durationMs, fileName, mimeType) " +
                    "VALUES ('Image', 'Frame', NULL, 14000, 'TikTok', 20, NULL, 'f.jpg', 'image/jpeg')",
            )
            old.execSQL(
                "INSERT INTO hopes (title, coverAssetId, backgroundAssetId, voiceAssetId, backgroundVolume, voiceVolume, " +
                    "trimStartMs, trimEndMs, voiceOffsetMs, createdAt) VALUES ('Respira', 2, 1, NULL, 30, 100, 4000, 48000, 2000, 30)",
            )
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '57e15652fb416a521b2366d28b979849')")
            old.version = 2
        }

        database = HopeDatabase.create(context, "migracion-2.db")
        val migratedAssets = AssetRepository(database, assetsDir)
        val migratedHopes = HopeRepository(database)

        assertEquals(listOf("f.jpg", "a.m4a"), migratedAssets.observe(null).first().map { it.fileName })
        val kept = migratedHopes.observe().first().single()
        assertEquals("Respira", kept.hope.title)
        assertEquals("f.jpg", kept.cover?.fileName)
        assertEquals("a.m4a", kept.background?.fileName)
        assertEquals(30, kept.hope.backgroundVolume)
        assertEquals(4_000L to 48_000L, kept.hope.trimStartMs to kept.hope.trimEndMs)
        assertEquals(2_000L, kept.hope.voiceOffsetMs)
        assertNull(kept.hope.linkUrl)

        // La hope migrada acepta un enlace y lo devuelve con su red.
        migratedHopes.save(kept.hope.copy(linkUrl = "https://youtu.be/jNQXAC9IVRw", linkNetwork = LinkNetwork.YouTube, linkTitle = "Me at the zoo"))
        val linked = migratedHopes.get(kept.hope.id)!!.hope
        assertEquals(LinkNetwork.YouTube, linked.linkNetwork)
        assertEquals("Me at the zoo", linked.linkTitle)
    }
}
