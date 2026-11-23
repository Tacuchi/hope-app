package dev.tacuchi.hope.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetDeletion
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.repository.AssetRepository
import dev.tacuchi.hope.domain.repository.HopeRepository
import dev.tacuchi.hope.domain.model.LinkNetwork
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.CoverFilter
import dev.tacuchi.hope.domain.model.CoverFrame
import dev.tacuchi.hope.domain.model.CoverTexture
import dev.tacuchi.hope.domain.model.DuckLevel
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.RoomEffect
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel
import dev.tacuchi.hope.domain.model.VoiceEffects
import dev.tacuchi.hope.domain.model.toMixSpec
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
        assets = RoomAssetRepository(database, assetsDir) { clock++ }
        hopes = RoomHopeRepository(database, assetsDir) { clock++ }
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

    /** Lo que se escribe por el contrato de dominio vuelve igual: assets, ajustes y enlace. */
    @Test
    fun aHopeWithItsAssetsAndSettingsSurvivesWritingAndReading() = runBlocking {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val voice = add(AssetNameKind.Recording, durationMs = 10_000)
        val cover = add(AssetNameKind.Frame)
        val written = Hope(
            title = "Antes de entrar", coverAssetId = cover.id, backgroundAssetId = background.id, voiceAssetId = voice.id,
            backgroundVolume = 35, voiceVolume = 90, trimStartMs = 4_000, trimEndMs = 48_000, voiceOffsetMs = 3_000,
            createdAt = 0, linkUrl = "https://youtu.be/jNQXAC9IVRw", linkNetwork = LinkNetwork.YouTube,
            linkTitle = "Me at the zoo", linkThumbnailUrl = "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg",
        )

        val id = hopes.save(written)
        val read = hopes.get(id)!!

        assertEquals(written.copy(id = id, createdAt = read.hope.createdAt), read.hope)
        assertEquals(Triple(cover, background, voice), Triple(read.cover, read.background, read.voice))
        assertEquals(read, hopes.observe().first().single())
        assertTrue(read.background!!.file.exists())
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
        assertTrue(background.file.exists())
        assertTrue(cover.file.exists())
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
        assertTrue(audio.file.exists())
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
        val migratedAssets = RoomAssetRepository(database, assetsDir)
        val migratedHopes = RoomHopeRepository(database, assetsDir)

        val kept = migratedAssets.observe(null).first()
        assertEquals(listOf("n.ogg", "c.mp3", "f.jpg", "a.ogg"), kept.map { it.file.name })
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
            // Tablas tal como las exporta core/data/schemas/…/2.json.
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
        val migratedAssets = RoomAssetRepository(database, assetsDir)
        val migratedHopes = RoomHopeRepository(database, assetsDir)

        assertEquals(listOf("f.jpg", "a.m4a"), migratedAssets.observe(null).first().map { it.file.name })
        val kept = migratedHopes.observe().first().single()
        assertEquals("Respira", kept.hope.title)
        assertEquals("f.jpg", kept.cover?.file?.name)
        assertEquals("a.m4a", kept.background?.file?.name)
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

    /**
     * Una base de la versión 3 con una hope de dos pistas, fondo recortado y voz con desfase abre
     * igual: la voz entera, sin fundidos, sin efectos y con su portada original. Sus assets y
     * archivos siguen ahí, y su mezcla es la de antes.
     */
    @Test
    fun migrationFromTheThirdVersionOpensOldHopesWholeAndWithoutEffects() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migracion-3.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            // Tablas tal como las exporta core/data/schemas/…/3.json.
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
                    "`createdAt` INTEGER NOT NULL, `linkUrl` TEXT, `linkNetwork` TEXT, `linkTitle` TEXT, `linkThumbnailUrl` TEXT, " +
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
                    "VALUES ('Audio', 'Recording', 1, NULL, 'Recording', 15, 41000, 'v.m4a', 'audio/mp4')",
            )
            old.execSQL(
                "INSERT INTO assets (type, nameKind, number, frameMs, origin, createdAt, durationMs, fileName, mimeType) " +
                    "VALUES ('Image', 'Frame', NULL, 14000, 'TikTok', 20, NULL, 'f.jpg', 'image/jpeg')",
            )
            old.execSQL(
                "INSERT INTO hopes (title, coverAssetId, backgroundAssetId, voiceAssetId, backgroundVolume, voiceVolume, " +
                    "trimStartMs, trimEndMs, voiceOffsetMs, createdAt, linkUrl, linkNetwork, linkTitle, linkThumbnailUrl) " +
                    "VALUES ('Antes de entrar', 3, 1, 2, 30, 90, 4000, 48000, 1500, 30, 'https://youtu.be/jNQXAC9IVRw', " +
                    "'YouTube', 'Me at the zoo', NULL)",
            )
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '314b3b032060da7ef4d990f03ba68132')")
            old.version = 3
        }

        database = HopeDatabase.create(context, "migracion-3.db")
        val migratedHopes = RoomHopeRepository(database, assetsDir)

        val kept = migratedHopes.observe().first().single()
        val hope = kept.hope
        assertEquals("Antes de entrar", hope.title)
        assertEquals(listOf("f.jpg", "a.m4a", "v.m4a"), listOf(kept.cover, kept.background, kept.voice).map { it?.file?.name })
        assertEquals(listOf(30, 90), listOf(hope.backgroundVolume, hope.voiceVolume))
        assertEquals(Triple(4_000L, 48_000L, 1_500L), Triple(hope.trimStartMs, hope.trimEndMs, hope.voiceOffsetMs))
        assertEquals(LinkNetwork.YouTube, hope.linkNetwork)
        // La voz entera y nada de efectos: suena y se ve como antes.
        assertEquals(0L to null, hope.voiceTrimStartMs to hope.voiceTrimEndMs)
        assertEquals(BackgroundEffects(), hope.backgroundEffects)
        assertEquals(VoiceEffects(), hope.voiceEffects)
        assertEquals(HopeEffects(), hope.hopeEffects)
        assertTrue(hope.coverEffects.isEmpty)
        // Fondo de 44 s; la voz entra a 1,5 s y dura 41 s: termina a los 42,5 s.
        assertEquals(44_000L, kept.durationMs)
        val mix = kept.toMixSpec()
        assertEquals(41_000L, mix.voiceSpanMs)
        assertTrue(mix.backgroundShape().isUnity && mix.voiceShape().isUnity)
        assertTrue(mix.presentation.coverEffects.isEmpty)

        // La hope migrada guarda y devuelve sus efectos nuevos.
        val withEffects = hope.copy(
            voiceTrimStartMs = 1_000, voiceTrimEndMs = 27_000,
            backgroundEffects = BackgroundEffects(Fades(2_000, 4_000), RoomEffect(RoomType.Hall, 30), SpeedLevel.Slow),
            voiceEffects = VoiceEffects(Fades(500, 1_000), RoomEffect(RoomType.Room, 20), clearVoice = true),
            hopeEffects = HopeEffects(Fades(1_000, 3_000), DuckLevel.Medium),
            coverEffects = CoverEffects(CoverFilter.Warm, 70, CoverFrame.Thin, CoverTexture.Grain, 30, 120, 0, -25),
        )
        migratedHopes.save(withEffects)
        assertEquals(withEffects, migratedHopes.get(hope.id)!!.hope)
    }
}
