import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ---- Release signing -----------------------------------------------------------------------
// Reads app/keystore.properties (normally never committed — see .gitignore and
// keystore.properties.example). Signing is enabled ONLY when that file exists, every one of
// the four required keys is present and non-blank, AND the keystore file it points to actually
// exists on disk. Any of those failing is reported as a clear warning (not a build failure),
// and the release build type is simply left unsigned — so `assembleDebug`/`assemble`/CI never
// crash over a missing, partial, or stale keystore.properties; only a fully-correct one signs.
val keystoreProps = Properties()
val keystorePropsFile = project.file("keystore.properties")
var hasSigningConfig = false
var resolvedKeystoreFile: File? = null
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
    val requiredKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val missing = requiredKeys.filter { keystoreProps.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        logger.warn("NH Files: app/keystore.properties is missing or has blank: ${missing.joinToString()}. Building an UNSIGNED release.")
    } else {
        val ksFile = project.file(keystoreProps.getProperty("storeFile"))
        if (!ksFile.exists()) {
            logger.warn("NH Files: keystore file \"${ksFile.path}\" (from storeFile= in keystore.properties) was not found. Building an UNSIGNED release.")
        } else {
            resolvedKeystoreFile = ksFile
            hasSigningConfig = true
        }
    }
}

android {
    namespace = "com.nhdeveloper.nhfiles"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nhdeveloper.nhfiles"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "9.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = resolvedKeystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by the FFmpeg decoder used by the built-in video player
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---- Built-in Video Player (View/XML based, merged from VideoPlayerOffline v1.4) ----
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // FFmpeg audio decoders (AC-3, E-AC-3, DTS, TrueHD...) - GPL-3.0, must match Media3 version
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+1")
    // ---- File manager ----
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
