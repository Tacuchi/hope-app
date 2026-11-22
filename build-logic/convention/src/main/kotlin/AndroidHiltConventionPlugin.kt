import org.gradle.api.Plugin
import org.gradle.api.Project

/** Hilt con su compilador por KSP. La app agrega lo que genera cada módulo. */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs.pluginId("ksp"))
        pluginManager.apply(libs.pluginId("hilt"))
        dependencies.add("implementation", libs.library("hilt-android"))
        dependencies.add("ksp", libs.library("hilt-compiler"))
    }
}
