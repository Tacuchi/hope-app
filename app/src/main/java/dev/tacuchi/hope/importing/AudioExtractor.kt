package dev.tacuchi.hope.importing

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Quita la pista de video y deja el audio en M4A con AAC, en el teléfono. Si entra AAC lo copia
 * sin recodificar; si entra otro códec (Opus, por ejemplo) lo convierte.
 *
 * Transformer está marcado como API inestable en Media3 1.11: puede cambiar entre versiones, así
 * que su uso queda encerrado en esta clase.
 */
@OptIn(UnstableApi::class)
class AudioExtractor(private val context: Context) {

    /**
     * Extrae el audio de [input] en [output] e informa el avance de 0 a 100 (nulo si aún no se
     * sabe). Devuelve la duración del audio en milisegundos. Cancelar la corrutina detiene la
     * extracción y borra [output].
     */
    suspend fun extract(input: File, output: File, onProgress: (Int?) -> Unit = {}): Long =
        withContext(Dispatchers.Main.immediate) {
            val started = SystemClock.elapsedRealtime()
            val result = CompletableDeferred<ExportResult>()
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        result.complete(exportResult)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        result.completeExceptionally(exportException)
                    }
                })
                .build()
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                .setRemoveVideo(true)
                .build()
            try {
                transformer.start(item, output.path)
                val progress = launch { reportProgress(transformer, onProgress) }
                val exportResult = result.await()
                progress.cancel()
                onProgress(100)
                Log.i(TAG, "Extracción terminada en ${SystemClock.elapsedRealtime() - started} ms")
                exportResult.approximateDurationMs
            } catch (error: Throwable) {
                transformer.cancel()
                output.delete()
                throw error
            }
        }

    private suspend fun reportProgress(transformer: Transformer, onProgress: (Int?) -> Unit) {
        val holder = ProgressHolder()
        while (true) {
            val state = transformer.getProgress(holder)
            onProgress(if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null)
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    private companion object {
        const val TAG = "HopeExtract"
        const val PROGRESS_INTERVAL_MS = 200L
    }
}
