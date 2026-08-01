import java.util.Properties

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val productVersionName = versionProperties.getProperty("VERSION_NAME")
    ?: error("VERSION_NAME is missing from version.properties")
val productVersionCode = versionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: error("VERSION_CODE must be an integer in version.properties")

val releaseSigningValues = listOf(
    "TABDECK_KEYSTORE_PATH",
    "TABDECK_KEYSTORE_PASSWORD",
    "TABDECK_KEY_ALIAS",
    "TABDECK_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
val releaseSigningConfigured = releaseSigningValues.values.all { !it.isNullOrBlank() }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.tabdeck.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tabdeck.app"
        minSdk = 26
        targetSdk = 36
        versionCode = productVersionCode
        versionName = productVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningValues["TABDECK_KEYSTORE_PATH"]))
                storePassword = requireNotNull(releaseSigningValues["TABDECK_KEYSTORE_PASSWORD"])
                keyAlias = requireNotNull(releaseSigningValues["TABDECK_KEY_ALIAS"])
                keyPassword = requireNotNull(releaseSigningValues["TABDECK_KEY_PASSWORD"])
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    val roomVersion = "2.8.4"

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-paging:$roomVersion")
    implementation("androidx.paging:paging-runtime:3.5.0")
    implementation("androidx.paging:paging-compose:3.5.0")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation("com.google.re2j:re2j:1.8")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20251224")
    testImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.paging:paging-testing:3.5.0")
}
