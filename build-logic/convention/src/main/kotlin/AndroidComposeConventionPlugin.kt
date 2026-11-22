import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Compose en un módulo Android ya configurado: compilador, BOM y herramientas de prueba. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        val android = extensions.getByName("android") as CommonExtension
        android.buildFeatures.compose = true
        val bom = dependencies.platform(libs.library("androidx-compose-bom"))
        dependencies.add("implementation", bom)
        dependencies.add("testImplementation", bom)
        dependencies.add("implementation", libs.library("androidx-compose-ui"))
        dependencies.add("implementation", libs.library("androidx-compose-ui-graphics"))
        dependencies.add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
        dependencies.add("implementation", libs.library("androidx-compose-material3"))
        dependencies.add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        // Registra la actividad vacía que usa createComposeRule en las pruebas de interfaz.
        dependencies.add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
        dependencies.add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
    }
}
