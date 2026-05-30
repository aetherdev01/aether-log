@file:Suppress("UnstableApiUsage")
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { props.load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace  = "com.aether.lv"
    compileSdk = 36

    defaultConfig {
        applicationId         = "com.aether.lv"
        minSdk                = 30          // Android 11
        targetSdk             = 36          // Android 16
        versionCode           = 130
        versionName           = "1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    ndkVersion = "27.2.12479018"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            storeFile     = localProps["STORE_FILE"]?.toString()?.let { rootProject.file(it) }
            storePassword = localProps["STORE_PASSWORD"]?.toString() ?: ""
            keyAlias      = localProps["KEY_ALIAS"]?.toString()      ?: ""
            keyPassword   = localProps["KEY_PASSWORD"]?.toString()   ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable           = false
            isPseudoLocalesEnabled = false
            isCrunchPngs           = true
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable        = true
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.map { it as BaseVariantOutputImpl }.forEach { output ->
            output.outputFileName =
                "loglog-v${variant.versionName}-${variant.buildType.name}.apk"
        }
    }


    lint {
        abortOnError       = false
        checkReleaseBuilds = false
        ignoreTestSources  = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
                "/META-INF/MANIFEST.MF",
                "**.proto",
                "kotlin/**",
                "META-INF/com/**"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore (tema / prefs)
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // Room (riwayat file)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Gson (serialisasi)
    implementation(libs.gson)

    // Material Components (XML themes)
    implementation(libs.material)

    // Accompanist
    implementation(libs.accompanist.systemuicontroller)

    // Unity Ads SDK
    implementation(libs.unity.ads)
}
