plugins {
    alias(libs.plugins.hope.android.library)
    alias(libs.plugins.hope.android.compose)
}

android {
    namespace = "dev.tacuchi.hope.ui"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
}
