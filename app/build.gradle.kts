import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.about.libraries)
}

configure<ApplicationExtension> {
    namespace = "se.arctosoft.vault"
    compileSdk = 36

    defaultConfig {
        applicationId = "se.arctosoft.vault"
        minSdk = 28
        targetSdk = 36
        versionCode = 41
        versionName = "2.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".dev"
        }
        //applicationVariants.all {
        //    val variant = this
        //    variant.outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
        //        .forEach { output ->
        //            val outputFileName =
        //                "Vault_${variant.versionCode}_${variant.versionName}_${variant.buildType.name}.apk"
        //            output.outputFileName = outputFileName
        //        }
        //}
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

}

dependencies {
    implementation(libs.core.ktx)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    testImplementation(libs.junit)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.preference)
    implementation(libs.activity)
    implementation(libs.biometrics)
    implementation(libs.documentfile)

    implementation(libs.security.crypto)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.preferences)
    annotationProcessor(libs.glide.annotation)

    implementation(libs.glide)
    implementation(libs.about.libraries)
    implementation(libs.about.libraries.compose)
}

aboutLibraries {
    collect {
        configPath = file("../config")
    }
    export {
        // Remove the "generated" timestamp to allow for reproducible builds
        excludeFields.addAll("generated")
    }
}

tasks.whenTaskAdded { // https://gist.github.com/obfusk/61046e09cee352ae6dd109911534b12e#fix-proposed-by-linsui-disable-baseline-profiles
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}