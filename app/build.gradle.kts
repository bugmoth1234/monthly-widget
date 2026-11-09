plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.monthly.widget"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.monthly.widget"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // 배포용 서명 키: 저장소에는 암호화된 파일(release.keystore.enc)만 있고,
    // GitHub Actions가 비밀값 KEYSTORE_PASSWORD로 풀어서 app/release.p12로 씁니다.
    val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
    signingConfigs {
        create("release") {
            storeFile = file("release.p12")
            storePassword = keystorePassword
            keyAlias = "release"
            keyPassword = keystorePassword
            storeType = "PKCS12"
        }
    }
    buildTypes {
        // 배포용: 디버그 모드가 꺼져 있어 USB 디버깅으로 앱 데이터를 꺼내 볼 수 없음
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
