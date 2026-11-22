import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Una función de la app: biblioteca Android con Compose, Hilt y sus ViewModels. Solo ve el
 * dominio y la UI compartida; la app la conecta con las demás.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("hope.android.library")
        pluginManager.apply("hope.android.compose")
        pluginManager.apply("hope.android.hilt")
        dependencies.add("implementation", dependencies.project(mapOf("path" to ":core:domain")))
        dependencies.add("implementation", dependencies.project(mapOf("path" to ":core:ui")))
        dependencies.add("implementation", libs.library("androidx-activity-compose"))
        dependencies.add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
        dependencies.add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
        dependencies.add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
        dependencies.add(
            "testImplementation",
            dependencies.testFixtures(dependencies.project(mapOf("path" to ":core:domain"))),
        )
    }
}
