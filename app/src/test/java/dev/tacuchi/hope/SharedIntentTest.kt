package dev.tacuchi.hope

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.testing.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SharedIntentTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Lo importado vive como archivos en `filesDir/assets/`. */
    private fun importedFiles() = File(context.filesDir, "assets").listFiles()?.size ?: 0

    private fun sharedImage(): Uri {
        val file = File(context.cacheDir, "compartida.png")
        file.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun recreatingTheActivityDoesNotImportTheSameShareAgain() {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, sharedImage())

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            waitUntil { importedFiles() == 1 }

            scenario.recreate()
            // Se da tiempo a una segunda importación, que no debe ocurrir.
            repeat(40) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }

            assertEquals(1, importedFiles())
        }
    }
}
