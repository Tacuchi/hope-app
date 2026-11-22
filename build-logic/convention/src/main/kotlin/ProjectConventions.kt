import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/** El catálogo `gradle/libs.versions.toml`, el mismo que usan los módulos. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId

/** JDK de compilación de todos los módulos, Android y JVM. */
internal const val JDK = 21

/**
 * Lo común a la app y a las bibliotecas Android. Las librerías de la línea base (core 1.19,
 * Compose BOM 2026.11) exigen compilar contra 37.1 o más; targetSdk sigue en 36 en la app.
 */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    android.defaultConfig.minSdk = 29
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_21
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_21
    android.testOptions.unitTests.isIncludeAndroidResources = true
    android.lint.warningsAsErrors = false
    android.lint.abortOnError = true
    configureKotlin()
}

internal fun Project.configureKotlin() {
    extensions.configure<KotlinProjectExtension> {
        jvmToolchain(JDK)
    }
}

/**
 * Un módulo sin `src/androidTest` no arma APK de prueba instrumentada: así `connectedDebugAndroidTest`
 * no instala ni desinstala nada en el dispositivo por un módulo que no tiene esas pruebas.
 */
internal val Project.hasDeviceTests: Boolean
    get() = file("src/androidTest").exists()

/** Pruebas locales con Robolectric, como en la app: JUnit 4 y AndroidX Test. */
internal fun Project.addLocalTestDependencies() {
    dependencies.add("testImplementation", libs.library("junit"))
    dependencies.add("testImplementation", libs.library("robolectric"))
    dependencies.add("testImplementation", libs.library("androidx-test-core"))
    dependencies.add("testImplementation", libs.library("androidx-test-ext-junit"))
}
