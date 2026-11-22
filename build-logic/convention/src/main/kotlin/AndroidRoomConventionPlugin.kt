import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Room con su compilador por KSP. El esquema se versiona en `schemas/` del módulo. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs.pluginId("ksp"))
        extensions.configure<KspExtension> {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
        dependencies.add("implementation", libs.library("androidx-room-runtime"))
        dependencies.add("implementation", libs.library("androidx-room-ktx"))
        dependencies.add("ksp", libs.library("androidx-room-compiler"))
    }
}
