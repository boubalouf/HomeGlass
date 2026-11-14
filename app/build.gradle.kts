import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.homeglass.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.homeglass.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("fr", "en")

        // OAuth Home Assistant : renseigner ces deux propriétés dans gradle.properties
        // (ou ~/.gradle/gradle.properties). Vides, le bouton OAuth est désactivé et le
        // jeton longue durée reste la méthode principale. Aucune URL n'est codée en dur.
        buildConfigField(
            "String",
            "HA_OAUTH_CLIENT_ID",
            "\"${project.findProperty("homeglass.oauthClientId") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "HA_OAUTH_REDIRECT_URI",
            "\"${project.findProperty("homeglass.oauthRedirectUri") ?: "homeglass://auth-callback"}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Contrat de mise en page et état applicatif.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Flou d'arrière-plan réel (Android 12+) avec repli translucide.
    implementation(libs.haze)

    // Home Assistant : REST + WebSocket, découverte mDNS, lien d'authentification.
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)

    // Réveil par la caméra frontale : analyse d'images et détection de visage embarquée.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.mlkit.face.detection)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
