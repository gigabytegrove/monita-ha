@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jmailen.kotlinter") version "5.1.1"
}

android {
    signingConfigs {
        create("monitaTest") {
            storeFile = file("../ci/monita-test.keystore")
            storePassword = "monita-test-only"
            keyAlias = "monita-test"
            keyPassword = "monita-test-only"
        }
    }
    namespace = "com.gigabytegrove.monita"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gigabytegrove.monita"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = (project.findProperty("releaseVersion") as String?) ?: "0.3.19-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        resValue("string", "app_name", "Monita")
        buildConfigField("String", "UI_BUILD_ID", "\"mobile-v0.3.19\"")
    }
    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-test"
            resValue("string", "app_name", "Monita")
            if (file("../ci/monita-test.keystore").exists()) {
                signingConfig = signingConfigs.getByName("monitaTest")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
        register("phoneTest") {
            initWith(getByName("release"))
            isDebuggable = false
            versionNameSuffix = "-test"
            resValue("string", "app_name", "Monita")
            if (file("../ci/monita-test.keystore").exists()) {
                signingConfig = signingConfigs.getByName("monitaTest")
            }
        }
        register("development") {
            applicationIdSuffix = ".dev"
            isDebuggable = true
            resValue("string", "app_name", "Monita DEV")
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
        }
    }
    lint {
        disable.add("GoogleAppIndexingWarning")
        lintConfig = file("../lint.xml")
    }
}

if (project.hasProperty("sign")) {
    android {
        signingConfigs {
            create("release") {
                storeFile = file(System.getenv("RELEASE_STORE_FILE"))
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    android.buildTypes.getByName("release").signingConfig = android.signingConfigs.getByName("release")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    val coilVersion = "2.7.0"
    val markwonVersion = "4.6.2"
    val tinylogVersion = "2.7.0"
    implementation(project(":client"))
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("com.github.cyb3rko:QuickPermissions-Kotlin:1.1.6")
    implementation("io.coil-kt:coil:$coilVersion")
    implementation("io.coil-kt:coil-svg:$coilVersion")
    implementation("io.noties.markwon:core:$markwonVersion")
    implementation("io.noties.markwon:image-coil:$markwonVersion")
    implementation("io.noties.markwon:image:$markwonVersion")
    implementation("io.noties.markwon:ext-tables:$markwonVersion")
    implementation("io.noties.markwon:ext-strikethrough:$markwonVersion")

    implementation("org.tinylog:tinylog-api-kotlin:$tinylogVersion")
    implementation("org.tinylog:tinylog-impl:$tinylogVersion")

    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")

}

