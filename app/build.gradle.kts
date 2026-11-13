import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Source unique de la version : fichier VERSION à la racine. versionCode = numéro de run CI.
val appVersionName: String = rootProject.file("VERSION").readText().trim()
val appVersionCode: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Signature : variables d'environnement (CI) ou keystore.properties local (jamais commité).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

val releaseStoreFile = signingValue("BULLE_KEYSTORE_PATH", "storeFile")

android {
    namespace = "com.karelisio.bulle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.karelisio.bulle"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GITHUB_REPO", "\"karelisio/bulle\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("BULLE_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("BULLE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("BULLE_KEY_PASSWORD", "keyPassword")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    androidResources {
        generateLocaleConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            // Miroir optionnel pour les jars Android de Robolectric (-Probolectric.repo=…).
            (project.findProperty("robolectric.repo") as String?)?.let {
                test.systemProperty("robolectric.dependency.repo.url", it)
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

// CHANGELOG.md (racine) embarqué dans les assets pour l'écran À propos.
abstract class CopyChangelogTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        source.get().asFile.copyTo(File(out, "CHANGELOG.md"), overwrite = true)
    }
}

val copyChangelogAsset = tasks.register<CopyChangelogTask>("copyChangelogAsset") {
    source.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyChangelogAsset, CopyChangelogTask::outputDir)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.datastore)
    implementation(libs.palette)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    // Shizuku (optionnel à l'exécution) : bascules système des réglages rapides du Volet.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // --- Zone packs de styles : QR code du partage de style (zxing core, encodeur seul, sans caméra). ---
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
