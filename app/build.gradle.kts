import java.util.Properties
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
        versionCode = 2
        versionName = "0.2.0"
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

    // Signature de release lue depuis .signing/keystore.properties (jamais commité).
    // Sans ce fichier, assembleRelease produit un APK non signé.
    val signingProperties = Properties().apply {
        val file = rootProject.file(".signing/keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    signingConfigs {
        if (signingProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Détecteur AGP 8.7 qui plante sur ce projet (IncompatibleClassChangeError) :
        // le désactiver évite de bloquer lintVitalRelease sur un bug de l'outil.
        disable += "NullSafeMutableLiveData"
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

    // Moteur Firefox embarqué : seule façon de lire le Web Player Spotify (DRM) dans l'app.
    implementation(libs.geckoview)

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
