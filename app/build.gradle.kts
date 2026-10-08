import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val appId = (project.findProperty("musibox.applicationId") as String?) ?: "br.com.musibox"

// Lê o google-services.json REAL (nunca é alterado nem substituído).
val googleServicesFile = file("google-services.json")

@Suppress("UNCHECKED_CAST")
val googleServices: Map<String, Any?> =
    if (googleServicesFile.exists()) {
        runCatching { groovy.json.JsonSlurper().parse(googleServicesFile) as Map<String, Any?> }.getOrDefault(emptyMap())
    } else {
        emptyMap()
    }

@Suppress("UNCHECKED_CAST")
val firebaseClients: List<Map<String, Any?>> = googleServices["client"] as? List<Map<String, Any?>> ?: emptyList()

@Suppress("UNCHECKED_CAST")
fun packageOf(client: Map<String, Any?>): String? =
    ((client["client_info"] as? Map<String, Any?>)?.get("android_client_info") as? Map<String, Any?>)
        ?.get("package_name") as? String

// Se o arquivo já tem um app com este package, usa o plugin oficial.
// Se não tem (o arquivo foi criado para outro app do mesmo projeto), o Firebase é iniciado
// em código com os dados do próprio arquivo; basta cadastrar este package no projeto.
val firebaseClient: Map<String, Any?>? =
    firebaseClients.firstOrNull { packageOf(it) == appId } ?: firebaseClients.firstOrNull()
val usePluginConfig = firebaseClients.any { packageOf(it) == appId }
if (usePluginConfig) {
    apply(plugin = "com.google.gms.google-services")
}

@Suppress("UNCHECKED_CAST")
fun firebaseValue(key: String): String {
    val project = googleServices["project_info"] as? Map<String, Any?> ?: emptyMap()
    val client = firebaseClient ?: emptyMap()
    return when (key) {
        "apiKey" -> ((client["api_key"] as? List<Map<String, Any?>>)?.firstOrNull()?.get("current_key") as? String)
        "appId" -> (client["client_info"] as? Map<String, Any?>)?.get("mobilesdk_app_id") as? String
        "projectId" -> project["project_id"] as? String
        "senderId" -> project["project_number"] as? String
        "storageBucket" -> project["storage_bucket"] as? String
        else -> null
    } ?: ""
}

/** Lê o Client ID do tipo Web (client_type 3) do google-services.json. */
@Suppress("UNCHECKED_CAST")
fun readWebClientId(): String =
    firebaseClients.asSequence()
        .flatMap { (it["oauth_client"] as? List<Map<String, Any?>> ?: emptyList()).asSequence() }
        .firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }
        ?.get("client_id") as? String ?: ""

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.musibox.app"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.2"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${readWebClientId()}\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", (firebaseClient != null).toString())
        buildConfigField("boolean", "FIREBASE_MANUAL_INIT", (firebaseClient != null && !usePluginConfig).toString())
        buildConfigField("String", "FB_API_KEY", "\"${firebaseValue("apiKey")}\"")
        buildConfigField("String", "FB_APP_ID", "\"${firebaseValue("appId")}\"")
        buildConfigField("String", "FB_PROJECT_ID", "\"${firebaseValue("projectId")}\"")
        buildConfigField("String", "FB_SENDER_ID", "\"${firebaseValue("senderId")}\"")
        buildConfigField("String", "FB_STORAGE_BUCKET", "\"${firebaseValue("storageBucket")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("musibox") {
                storeFile = rootProject.file("keystore/" + File(keystoreProps.getProperty("storeFile")).name)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("musibox")
        }
        debug {
            signingConfig = signingConfigs.findByName("musibox")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Necessário para o motor yt-dlp (python/ffmpeg empacotados como bibliotecas nativas).
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "/META-INF/*.kotlin_module",
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Banco local e preferências
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Tarefas em segundo plano
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Player
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.google.guava:guava:33.3.1-android")

    // Imagens
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // Segurança do cofre
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.crypto.tink:tink-android:1.15.0")

    // Conta Google + Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Motor de download (yt-dlp + ffmpeg)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
