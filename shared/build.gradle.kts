plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.android.library)
}

// Pacote determinístico para o Res gerado a partir de
// commonMain/composeResources (fontes Sora/Manrope).
compose {
    resources {
        packageOfResClass = "app.constancia.shared.generated.resources"
    }
}

kotlin {
    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.material.icons.extended)
            // Fontes e recursos via composeResources (Res.font.*).
            implementation(compose.components.resources)
            // Banco local (fonte de verdade, offline-first).
            implementation(libs.sqldelight.runtime)
            // asFlow()/mapToList() das queries (usado nos repositórios).
            implementation(libs.sqldelight.coroutines.extensions)
            // Preferências chave-valor multiplataforma (tema, onboarding, status Pro).
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "app.constancia.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("ConstanciaDb") {
            packageName.set("app.constancia.db")
        }
    }
}
