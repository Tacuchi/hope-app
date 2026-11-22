plugins {
    alias(libs.plugins.hope.android.feature)
}

android {
    namespace = "dev.tacuchi.hope.ui.settings"
}

dependencies {
    implementation(libs.aboutlibraries.compose)
}
