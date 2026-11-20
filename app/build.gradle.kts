import java.util.Properties

plugins {
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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
    // Las librerías de la línea base (core 1.19, Compose BOM 2026.11) exigen compilar contra 37.1 o más.
    // targetSdk sigue en 36: el comportamiento en ejecución no cambia.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dev.tacuchi.hope"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(21)
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

// El esquema de Room se versiona para poder escribir migraciones.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.aboutlibraries.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.transformer)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // Registra la actividad vacía que usa createComposeRule en las pruebas de interfaz.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
