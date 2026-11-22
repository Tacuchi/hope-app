package dev.tacuchi.hope.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent
import dev.tacuchi.hope.data.LocalCatalog
import dev.tacuchi.hope.data.OEmbedLinkPreviews
import dev.tacuchi.hope.data.PreferencesAppearanceRepository
import dev.tacuchi.hope.domain.media.AssetPreview
import dev.tacuchi.hope.domain.media.AudioExtractor
import dev.tacuchi.hope.domain.media.AudioOutputs
import dev.tacuchi.hope.domain.media.FrameGrabber
import dev.tacuchi.hope.domain.media.HopePlayer
import dev.tacuchi.hope.domain.media.ImportStaging
import dev.tacuchi.hope.domain.media.MediaInspector
import dev.tacuchi.hope.domain.media.VoiceRecorder
import dev.tacuchi.hope.domain.repository.AppearanceRepository
import dev.tacuchi.hope.domain.repository.AssetRepository
import dev.tacuchi.hope.domain.repository.HopeRepository
import dev.tacuchi.hope.domain.repository.LinkPreviewRepository
import dev.tacuchi.hope.importing.AndroidMediaInspector
import dev.tacuchi.hope.importing.ContentImportStaging
import dev.tacuchi.hope.importing.RetrieverFrameGrabber
import dev.tacuchi.hope.importing.TransformerAudioExtractor
import dev.tacuchi.hope.playback.MixPlayer
import dev.tacuchi.hope.preview.AssetAudioPlayer
import dev.tacuchi.hope.recording.MediaRecorderCapture
import dev.tacuchi.hope.recording.MediaVoiceRecorder
import dev.tacuchi.hope.recording.SystemAudioOutputs
import java.io.File
import javax.inject.Singleton

/**
 * Lo que vive mientras vive el proceso: se crea una sola vez. Cada contrato del dominio se
 * conecta aquí con su implementación de datos o de medios. Los nombres de la base, las carpetas
 * de `filesDir` y las preferencias son los mismos desde la 0.1.0.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun appearanceRepository(@ApplicationContext context: Context): AppearanceRepository =
        PreferencesAppearanceRepository(context)

    @Provides
    @Singleton
    fun catalog(@ApplicationContext context: Context) = LocalCatalog(context)

    @Provides
    fun assetRepository(catalog: LocalCatalog): AssetRepository = catalog.assets

    @Provides
    fun hopeRepository(catalog: LocalCatalog): HopeRepository = catalog.hopes

    @Provides
    @Singleton
    fun linkPreviews(): LinkPreviewRepository = OEmbedLinkPreviews()

    @Provides
    @Singleton
    fun staging(@ApplicationContext context: Context): ImportStaging =
        ContentImportStaging(context.contentResolver, File(context.filesDir, "import-tmp"))

    @Provides
    @Singleton
    fun inspector(): MediaInspector = AndroidMediaInspector()

    @Provides
    @Singleton
    fun audioExtractor(@ApplicationContext context: Context): AudioExtractor = TransformerAudioExtractor(context)

    @Provides
    @Singleton
    fun frameGrabber(): FrameGrabber = RetrieverFrameGrabber()

    /**
     * El único reproductor de hopes del proceso: el editor, la biblioteca y el servicio de
     * reproducción comparten esta instancia, así la notificación controla lo que se oye.
     */
    @Provides
    @Singleton
    fun mixPlayer(@ApplicationContext context: Context) = MixPlayer(context)

    @Provides
    @Singleton
    fun voiceRecorder(@ApplicationContext context: Context): VoiceRecorder =
        MediaVoiceRecorder(MediaRecorderCapture(context), File(context.filesDir, "recording-tmp"))

    @Provides
    @Singleton
    fun audioOutputs(@ApplicationContext context: Context): AudioOutputs = SystemAudioOutputs(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    abstract fun hopePlayer(mix: MixPlayer): HopePlayer
}

/** Lo que pertenece a un solo ViewModel y se libera con él. */
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {

    /** Una preescucha por cada pantalla de Assets; la comparten sus casos de uso y su ViewModel la libera al salir. */
    @Provides
    @ViewModelScoped
    fun assetPreview(@ApplicationContext context: Context): AssetPreview = AssetAudioPlayer(context)
}
