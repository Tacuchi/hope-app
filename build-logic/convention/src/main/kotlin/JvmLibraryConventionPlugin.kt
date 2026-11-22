import org.gradle.api.Plugin
import org.gradle.api.Project

/** Módulo Kotlin/JVM puro, sin Android: el dominio. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        configureKotlin()
        dependencies.add("testImplementation", libs.library("junit"))
    }
}
