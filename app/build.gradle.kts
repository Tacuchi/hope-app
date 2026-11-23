import java.util.Properties

plugins {
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.hope.android.application)
    alias(libs.plugins.hope.android.compose)
    alias(libs.plugins.hope.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Firma de lanzamiento. La clave y sus contraseñas viven fuera del repo, en un archivo local
 * (formato en el README). Otra ruta se puede indicar con `-Phope.signing=<archivo>` o con la
 * variable `HOPE_SIGNING_PROPERTIES`. Sin el archivo, la versión de desarrollo compila igual y
 * empaquetar la de lanzamiento falla con un mensaje que dice qué archivo crear.
 */
class UploadSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

val uploadSigningFile: File = providers.gradleProperty("hope.signing")
    .orElse(providers.environmentVariable("HOPE_SIGNING_PROPERTIES"))
    .map { File(it) }
    .getOrElse(File(System.getProperty("user.home"), ".android-keys/hope-upload.properties"))

val uploadSigning: UploadSigning? = uploadSigningFile.takeIf { it.isFile }?.let { file ->
    val properties = Properties().apply { file.inputStream().use { load(it) } }
    fun required(key: String) = properties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Falta «$key» en ${file.path}. Debe tener storeFile, storePassword, keyAlias y keyPassword.")
    val store = File(required("storeFile")).let { if (it.isAbsolute) it else File(file.parentFile, it.path) }
    if (!store.isFile) throw GradleException("No existe el almacén de claves ${store.path}, indicado en ${file.path}.")
    UploadSigning(store, required("storePassword"), required("keyAlias"), required("keyPassword"))
}

android {
    namespace = "dev.tacuchi.hope"
    defaultConfig {
        applicationId = "dev.tacuchi.hope"
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        uploadSigning?.let { upload ->
            create("upload") {
                storeFile = upload.storeFile
                storePassword = upload.storePassword
                keyAlias = upload.keyAlias
                keyPassword = upload.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
            // Tabla de símbolos de las bibliotecas nativas, para que Play muestre nombres de función en las trazas.
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
        }
    }

    buildFeatures {
        buildConfig = true
    }

}

// Sin firma, el .aab o el .apk de lanzamiento saldrían sin firmar: mejor fallar y decir qué falta.
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    val missing = uploadSigning == null
    val path = uploadSigningFile.path
    doFirst {
        if (missing) throw GradleException(
            "Falta el archivo de firma de lanzamiento: $path\n" +
                "Créalo con storeFile, storePassword, keyAlias y keyPassword (ver «Firmar la versión de lanzamiento» en el README). " +
                "La versión de desarrollo no lo necesita."
        )
    }
}
tasks.configureEach {
    if (name in setOf("packageRelease", "packageReleaseBundle", "signReleaseBundle")) dependsOn(checkReleaseSigning)
}

dependencies {
    // La app reúne las features y las implementaciones de los contratos del dominio.
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(project(":core:ui"))
    implementation(project(":feature:library"))
    implementation(project(":feature:assets"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:importing"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
