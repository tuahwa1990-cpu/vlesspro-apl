plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.vlesspro.pemasang"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vlesspro.pemasang"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("vCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("vName") as String?) ?: "0.0.1"
        (project.findProperty("idSuffix") as String?)?.let { applicationIdSuffix = it }
        manifestPlaceholders["etiket"] = (project.findProperty("etiket") as String?) ?: "VLESS Pro"
    }
    signingConfigs {
        create("rel") {
            val ks = System.getenv("KS_PATH")
            if (ks != null) { storeFile = file(ks); storePassword = System.getenv("KS_PASS"); keyAlias = "vp"; keyPassword = System.getenv("KS_PASS") }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KS_PATH") != null) signingConfigs.getByName("rel") else signingConfigs.getByName("debug")
        }
    }
    packaging { jniLibs { useLegacyPackaging = true }; resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/versions/**", "META-INF/MANIFEST.MF") } }
    lint { checkReleaseBuilds = false; abortOnError = false }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.github.mwiede:jsch:0.2.20")
}
