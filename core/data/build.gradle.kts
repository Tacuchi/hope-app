plugins {
    alias(libs.plugins.hope.android.library)
    alias(libs.plugins.hope.android.room)
}

android {
    namespace = "dev.tacuchi.hope.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
}
