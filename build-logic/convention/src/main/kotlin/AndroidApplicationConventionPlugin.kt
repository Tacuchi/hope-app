import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** La app: SDK, JDK y pruebas comunes. Firma, identidad y versión quedan en su propio build. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig.targetSdk = 36
        }
        if (!hasDeviceTests) {
            extensions.configure<ApplicationAndroidComponentsExtension> {
                beforeVariants { it.androidTest.enable = false }
            }
        }
        addLocalTestDependencies()
    }
}
