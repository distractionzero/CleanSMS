import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.Executors

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

val releaseStoreFile = providers.gradleProperty("CLEANSMS_STORE_FILE").orNull

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

abstract class FetchSenderIcons : DefaultTask() {
    @get:InputFile abstract val headers: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    private val domainPattern = Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+")

    @TaskAction
    fun fetch() {
        val dir = outputDir.get().dir("sender_icons").asFile.apply { mkdirs() }
        val entries = groovy.json.JsonSlurper().parse(headers.get().asFile) as Map<*, *>
        val domains = entries.values.mapNotNull { (it as Map<*, *>)["domain"] as? String }
            .filter { domainPattern.matches(it) && !File(dir, "$it.png").exists() }
            .toSet()
        val pool = Executors.newFixedThreadPool(8)
        domains.map { pool.submit { download(it, File(dir, "$it.png")) } }.forEach { it.get() }
        pool.shutdown()
    }

    private fun download(domain: String, target: File) {
        runCatching {
            val conn = URI("https://iconaut.xyz/icon?domain=$domain&size=128").toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            if (conn.responseCode == 200 && conn.contentType.orEmpty().startsWith("image/png")) {
                target.writeBytes(conn.inputStream.use { it.readBytes() })
            }
        }.onFailure { logger.warn("Sender icon fetch failed for $domain: ${it.message}") }
    }
}

val fetchSenderIcons = tasks.register<FetchSenderIcons>("fetchSenderIcons") {
    headers = layout.projectDirectory.file("src/main/assets/sender_headers.json")
    outputDir = layout.projectDirectory.dir("sender-icons")
}

tasks.named("preBuild") { dependsOn(fetchSenderIcons) }

android {
    namespace = "dev.codingcoffee.cleansms"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.codingcoffee.cleansms"
        minSdk = 29
        targetSdk = 37
        versionCode = 6
        versionName = "0.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SMS_MODEL_ASSET", "\"$smsModelAsset\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("CLEANSMS_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("CLEANSMS_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("CLEANSMS_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
        }
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
        variant.sources.assets?.addStaticSourceDirectory("sender-icons")
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