import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.scanner.offline"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scanner.offline"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk {
            // 默认仅 64 位 ARM，覆盖 95%+ 现代 Android 手机；APK 减半。
            // CI 跑 connectedAndroidTest 时（GitHub runner 的 emulator 是 x86_64），
            // 通过环境变量 CI_ANDROID_TEST=true 临时把 x86_64 加进来，
            // 这样不污染默认的 release/debug APK，也不需要单独的 build variant。
            abiFilters += listOf("arm64-v8a")
            if (System.getenv("CI_ANDROID_TEST") == "true") {
                abiFilters += "x86_64"
            }
        }
    }

    // ─── 发布签名配置 ─────────────────────────────────────
    // 本地与 CI 都从环境变量读取签名信息，不在仓库里硬编码任何路径或密码。
    //
    // 必须的 4 个变量：
    //   OFFSCAN_KEYSTORE_BASE64   keystore 文件的 base64 编码（CI 友好）
    //   OFFSCAN_KEYSTORE_PASSWORD keystore 密码
    //   OFFSCAN_KEY_ALIAS         证书 alias
    //   OFFSCAN_KEY_PASSWORD      证书 password
    //
    // 任意一个缺失就跳过签名（release APK 仍能编译，但产物 unsigned）。
    val keystoreBase64 = System.getenv("OFFSCAN_KEYSTORE_BASE64").orEmpty()
    val keystorePassword = System.getenv("OFFSCAN_KEYSTORE_PASSWORD").orEmpty()
    val keyAlias = System.getenv("OFFSCAN_KEY_ALIAS").orEmpty()
    val keyPassword = System.getenv("OFFSCAN_KEY_PASSWORD").orEmpty()
    val canSignRelease = keystoreBase64.isNotBlank() &&
        keystorePassword.isNotBlank() &&
        keyAlias.isNotBlank() &&
        keyPassword.isNotBlank()

    // 调试日志：打印 env var 是否存在（不泄漏值），帮助排查 CI 签名问题。
    // 用 logger.lifecycle 而非 println —— println 在 configuration cache 启用时会被
    // Gradle 重定向到 INFO 级别，CI 默认 LIFECYCLE 级别看不到。
    logger.lifecycle("[OffScan signing] OFFSCAN_KEYSTORE_BASE64 length=${keystoreBase64.length}")
    logger.lifecycle("[OffScan signing] OFFSCAN_KEYSTORE_PASSWORD blank=${keystorePassword.isBlank()}")
    logger.lifecycle("[OffScan signing] OFFSCAN_KEY_ALIAS blank=${keyAlias.isBlank()}")
    logger.lifecycle("[OffScan signing] OFFSCAN_KEY_PASSWORD blank=${keyPassword.isBlank()}")
    logger.lifecycle("[OffScan signing] canSignRelease=$canSignRelease")

    // signingConfigs 始终声明 release block（哪怕 secret 缺失），
    // 这样 buildTypes.release.signingConfig 引用时不会崩；
    // storeFile 如果没有 secret 就指向一个不存在的占位路径，AGP 会因此自动
    // 输出 unsigned APK 而非 signed APK。
    signingConfigs {
        create("release") {
            if (canSignRelease) {
                val keystoreFile = layout.buildDirectory.file("ci-keystore.jks").get().asFile.apply {
                    parentFile.mkdirs()
                    writeBytes(Base64.getDecoder().decode(keystoreBase64))
                }
                logger.lifecycle("[OffScan signing] Wrote keystore to: ${keystoreFile.absolutePath} (${keystoreFile.length()} bytes)")
                storeFile = keystoreFile
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 没配 keystore 时 release APK 会是 unsigned；
            // CI 流程 (.github/workflows/release.yml) 的 verify 步骤会捕获并报错。
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
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
    androidResources {
        // PaddleOCR 后续会用到的离线模型（.nb / .tflite）不要被压缩
        noCompress += listOf("nb", "tflite", "txt")
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // MLKit OCR
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coil
    implementation(libs.coil.compose)

    // Accompanist - 权限
    implementation(libs.accompanist.permissions)

    // OpenCV - 边缘检测 / 透视矫正 / 去阴影
    implementation(libs.opencv)

    // RapidOCR - 中文/混合识别（基于 PaddleOCR PP-OCRv4 模型，ONNX Runtime 推理，自带模型）
    implementation(libs.rapidocr.android)

    // SAF 文件操作封装（用户可自定义导出目录）
    implementation(libs.androidx.documentfile)

    // 注：Word/Excel 通过自实现 OoxmlWriter 生成（轻量、零依赖），无需 Apache POI

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
