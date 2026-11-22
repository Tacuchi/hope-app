plugins {
    alias(libs.plugins.hope.android.library)
    // PlaybackService recibe la mezcla del grafo de la app.
    alias(libs.plugins.hope.android.hilt)
}

android {
    namespace = "dev.tacuchi.hope.media"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.transformer)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
