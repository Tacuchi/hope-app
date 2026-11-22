package dev.tacuchi.hope.di

import android.content.Context
import android.content.Intent
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tacuchi.hope.data.AppearanceRepository
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.HopeDatabase
import dev.tacuchi.hope.data.HopeRepository
import dev.tacuchi.hope.data.PreferencesAppearanceStore
import dev.tacuchi.hope.importing.AudioExtractor
import dev.tacuchi.hope.importing.ImportStaging
import dev.tacuchi.hope.importing.MediaInspector
import dev.tacuchi.hope.links.LinkPreviews
import dev.tacuchi.hope.playback.HopePlayer
import dev.tacuchi.hope.playback.MixPlayer
import dev.tacuchi.hope.playback.PlaybackService
import dev.tacuchi.hope.recording.AudioOutputs
import dev.tacuchi.hope.recording.MediaRecorderCapture
import dev.tacuchi.hope.recording.VoiceRecorder
import dev.tacuchi.hope.ui.assets.AssetAudioPlayer
import dev.tacuchi.hope.ui.assets.AssetPreview
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** Pregunta en el momento si hay audífonos conectados. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HeadphonesConnected

/**
 * Lo que vive mientras vive el proceso: se crea una sola vez. Los nombres de la base, las
 * carpetas de `filesDir` y las preferencias son los mismos desde la 0.1.0.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun appearanceRepository(@ApplicationContext context: Context) =
        AppearanceRepository(PreferencesAppearanceStore(context))

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HopeDatabase = HopeDatabase.create(context)

    @Provides
    @Singleton
    fun assetRepository(@ApplicationContext context: Context, database: HopeDatabase) =
        AssetRepository(database, File(context.filesDir, "assets"))

    @Provides
    @Singleton
    fun hopeRepository(database: HopeDatabase) = HopeRepository(database)

    @Provides
    @Singleton
    fun staging(@ApplicationContext context: Context) =
        ImportStaging(context.contentResolver, File(context.filesDir, "import-tmp"))

    @Provides
    @Singleton
    fun inspector() = MediaInspector()

    @Provides
    @Singleton
    fun audioExtractor(@ApplicationContext context: Context) = AudioExtractor(context)

    /**
     * El único reproductor de hopes del proceso: el editor, la biblioteca y [PlaybackService]
     * comparten esta instancia, así la notificación controla lo que se oye.
     */
    @Provides
    @Singleton
    fun mixPlayer(@ApplicationContext context: Context) = MixPlayer(context, null) {
        context.startService(Intent(context, PlaybackService::class.java))
    }

    @Provides
    @Singleton
    fun voiceRecorder(@ApplicationContext context: Context) =
        VoiceRecorder(MediaRecorderCapture(context), File(context.filesDir, "recording-tmp"))

    @Provides
    @Singleton
    fun audioOutputs(@ApplicationContext context: Context) = AudioOutputs(context)

    @Provides
    @HeadphonesConnected
    fun headphonesConnected(outputs: AudioOutputs): () -> Boolean = outputs::headphonesConnected

    @Provides
    @Singleton
    fun linkPreviews() = LinkPreviews()
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

    /** Una preescucha nueva por cada pantalla de Assets; su ViewModel la libera al salir. */
    @Provides
    fun assetPreview(@ApplicationContext context: Context): AssetPreview = AssetAudioPlayer(context)
}
