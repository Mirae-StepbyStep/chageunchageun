plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mirae.chageunchageun"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mirae.chageunchageun"
        minSdk = 28 // Pleos Playground 배포 앱 요구사항
        targetSdk = 35 // Sample App 기준
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // AAOS Car API (CarPropertyManager, CarUxRestrictionsManager). platform/car 에서만 사용합니다.
    useLibrary("android.car")

    // Release 서명. 키스토어와 비밀번호는 저장소 밖에 둡니다: ~/.gradle/gradle.properties 또는 같은 이름의 환경 변수.
    // 값이 없으면 release는 서명 없이 빌드됩니다(debug 빌드와 테스트에는 필요 없음).
    fun signingValue(name: String): String? =
        providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull
    val releaseStoreFile = signingValue("CHAGEUN_RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("CHAGEUN_RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("CHAGEUN_RELEASE_KEY_ALIAS")
                keyPassword = signingValue("CHAGEUN_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Pleos Vehicle SDK. import는 platform/vehicle 에서만 합니다.
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
