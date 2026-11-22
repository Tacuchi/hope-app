plugins {
    alias(libs.plugins.hope.jvm.library)
    // Los dobles de los contratos que comparten las pruebas de varios módulos.
    `java-test-fixtures`
}

dependencies {
    implementation(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
}
