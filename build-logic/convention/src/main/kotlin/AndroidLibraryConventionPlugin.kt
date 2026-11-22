import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Biblioteca Android con Kotlin, el mismo SDK que la app y pruebas locales con Robolectric. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        if (!hasDeviceTests) {
            extensions.configure<LibraryAndroidComponentsExtension> {
                beforeVariants { it.androidTest.enable = false }
            }
        }
        addLocalTestDependencies()
    }
}
