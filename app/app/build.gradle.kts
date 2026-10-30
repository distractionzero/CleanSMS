import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

val releaseStoreFile = providers.gradleProperty("BOUNCER_STORE_FILE").orNull

val smsModelVersion = "v0.0.1"
val smsModelSha256 = "de12226aa830b67a556d4b829dfb272ef3ef5ef6937ae82f4620c3851153e8ef"
val smsModelAsset = "sms-model-$smsModelVersion.bin"

abstract class DownloadSmsModel : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input abstract val fileName: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val bytes = URI(url.get()).toURL().openStream().use { it.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(actual == sha256.get()) { "SMS model checksum mismatch: expected ${sha256.get()}, got $actual" }
        outputDir.get().file(fileName.get()).asFile.writeBytes(bytes)
    }
}

val downloadSmsModel = tasks.register<DownloadSmsModel>("downloadSmsModel") {
    url = "https://github.com/distractionzero/textonomy/releases/download/$smsModelVersion/sms-model.bin"
    sha256 = smsModelSha256
    fileName = smsModelAsset
    outputDir = layout.buildDirectory.dir("generated/smsModel")
}

android {
    namespace = "dev.codingcoffee.bouncer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.codingcoffee.bouncer"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SMS_MODEL_ASSET", "\"$smsModelAsset\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("BOUNCER_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("BOUNCER_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("BOUNCER_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadSmsModel, DownloadSmsModel::outputDir)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.aboutlibraries.compose.m3)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}