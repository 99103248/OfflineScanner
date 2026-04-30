# Third-Party Licenses

OffScan is licensed under the **Apache License 2.0**. This document lists all third-party dependencies bundled into the application binary, their respective licenses, and links to upstream sources.

> Last updated: 2026-04-30 · See `gradle/libs.versions.toml` for exact pinned versions.

---

## Summary by License

| License | Count | Project Compatibility |
|---|---|---|
| **Apache License 2.0** | 30+ | ✅ Same license as OffScan |
| **MIT License** | 2 | ✅ Compatible |
| **Eclipse Public License 1.0** | 1 (test only) | ✅ Test-only, not redistributed in APK |
| **Proprietary (free use granted)** | 2 (Google ML Kit) | ✅ Compatible — see below |

---

## 1. AndroidX & Jetpack Libraries (Apache 2.0)

| Artifact | Version | License |
|---|---|---|
| `androidx.core:core-ktx` | 1.13.1 | Apache 2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | Apache 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | Apache 2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | Apache 2.0 |
| `androidx.activity:activity-compose` | 1.9.3 | Apache 2.0 |
| `androidx.compose.bom` | 2024.12.01 | Apache 2.0 |
| `androidx.compose.ui:ui` | (BOM) | Apache 2.0 |
| `androidx.compose.ui:ui-graphics` | (BOM) | Apache 2.0 |
| `androidx.compose.ui:ui-tooling` | (BOM) | Apache 2.0 |
| `androidx.compose.ui:ui-tooling-preview` | (BOM) | Apache 2.0 |
| `androidx.compose.material3:material3` | (BOM) | Apache 2.0 |
| `androidx.compose.material:material-icons-extended` | (BOM) | Apache 2.0 |
| `androidx.navigation:navigation-compose` | 2.8.5 | Apache 2.0 |
| `androidx.hilt:hilt-navigation-compose` | 1.2.0 | Apache 2.0 |
| `androidx.camera:camera-core` | 1.4.1 | Apache 2.0 |
| `androidx.camera:camera-camera2` | 1.4.1 | Apache 2.0 |
| `androidx.camera:camera-lifecycle` | 1.4.1 | Apache 2.0 |
| `androidx.camera:camera-view` | 1.4.1 | Apache 2.0 |
| `androidx.room:room-runtime` | 2.6.1 | Apache 2.0 |
| `androidx.room:room-ktx` | 2.6.1 | Apache 2.0 |
| `androidx.room:room-compiler` | 2.6.1 | Apache 2.0 |
| `androidx.documentfile:documentfile` | 1.0.1 | Apache 2.0 |

**Source:** https://android.googlesource.com/platform/frameworks/support/  
**License text:** https://www.apache.org/licenses/LICENSE-2.0

---

## 2. Kotlin & Coroutines (Apache 2.0)

| Artifact | Version | License |
|---|---|---|
| Kotlin (compiler / stdlib) | 2.0.21 | Apache 2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | Apache 2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | 1.9.0 | Apache 2.0 |

**Source:** https://github.com/JetBrains/kotlin · https://github.com/Kotlin/kotlinx.coroutines

---

## 3. Hilt / Dagger (Apache 2.0)

| Artifact | Version | License |
|---|---|---|
| `com.google.dagger:hilt-android` | 2.52 | Apache 2.0 |
| `com.google.dagger:hilt-android-compiler` | 2.52 | Apache 2.0 |

**Source:** https://github.com/google/dagger

---

## 4. Coil (Apache 2.0)

| Artifact | Version | License |
|---|---|---|
| `io.coil-kt:coil-compose` | 2.7.0 | Apache 2.0 |

**Source:** https://github.com/coil-kt/coil

---

## 5. Accompanist (Apache 2.0)

| Artifact | Version | License |
|---|---|---|
| `com.google.accompanist:accompanist-permissions` | 0.37.0 | Apache 2.0 |

**Source:** https://github.com/google/accompanist

---

## 6. OpenCV (Apache 2.0)

| Artifact | Version | License |
|---|---|---|
| `org.opencv:opencv` | 4.10.0 | Apache 2.0 |

> OpenCV switched from 3-clause BSD to **Apache 2.0** starting with version 4.5.0. Version 4.10.0 used here is fully Apache 2.0.

**Source:** https://github.com/opencv/opencv  
**License page:** https://opencv.org/license/

---

## 7. RapidOCR & PaddleOCR Models (Apache 2.0)

| Artifact | Version | License |
|---|---|---|
| `io.github.hzkitty:rapidocr4j-android` | 1.0.0 | Apache 2.0 |
| **PP-OCRv4 detection / recognition / classifier models** (bundled in the AAR) | — | Apache 2.0 |

**Sources:**
- RapidOCR4j-Android: https://github.com/hzkitty/RapidOCR4j-Android
- PaddleOCR (upstream model authors): https://github.com/PaddlePaddle/PaddleOCR

> The PP-OCRv4 pre-trained models are released by PaddlePaddle Authors under Apache 2.0, including for commercial use. See [PaddleOCR Issue #8780](https://github.com/PaddlePaddle/PaddleOCR/issues/8780) for the official statement.

---

## 8. ONNX Runtime (MIT)

ONNX Runtime is pulled in transitively by `rapidocr4j-android` to execute the PP-OCRv4 models on-device.

| Artifact | License |
|---|---|
| `com.microsoft.onnxruntime:onnxruntime-android` | **MIT** |

**Source:** https://github.com/microsoft/onnxruntime  
**License text:** https://opensource.org/licenses/MIT

---

## 9. Google ML Kit — Proprietary, Free Use Granted

| Artifact | Version | Terms |
|---|---|---|
| `com.google.mlkit:text-recognition` | 16.0.1 | ML Kit Terms of Service |
| `com.google.mlkit:text-recognition-chinese` | 16.0.1 | ML Kit Terms of Service |

**Important notes:**

- ML Kit binaries and on-device models are **proprietary** Google software — they are **NOT** covered by the Apache 2.0 license that applies to the rest of OffScan.
- The ML Kit Terms of Service (https://developers.google.com/ml-kit/terms) grant free, royalty-free use in any application — commercial or open-source — subject to the following constraint: **users may not reverse-engineer ML Kit binaries or attempt to extract their source code or models.**
- ML Kit runs entirely on-device for the text recognition APIs used here. No data leaves the user's phone (Google's privacy policy and our `AndroidManifest.xml` both confirm this — we explicitly remove the `INTERNET` permission).
- If you fork OffScan, the ML Kit usage continues to be governed by Google's terms regardless of the license you apply to your fork.

---

## 10. Test-Only Dependencies (Not Redistributed)

These dependencies are pulled in by `testImplementation` / `androidTestImplementation` and **do not ship in the production APK**:

| Artifact | Version | License |
|---|---|---|
| `junit:junit` | 4.13.2 | Eclipse Public License 1.0 |
| `androidx.test.ext:junit` | 1.2.1 | Apache 2.0 |
| `androidx.test:runner` | 1.6.2 | Apache 2.0 |
| `androidx.test:rules` | 1.6.1 | Apache 2.0 |
| `androidx.test.espresso:espresso-core` | 3.6.1 | Apache 2.0 |
| `org.mockito:mockito-core` | 5.14.2 | MIT |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 | Apache 2.0 |

---

## 11. Build Tools (Not Redistributed)

These tools are used at build time only and **do not become part of the APK**:

| Tool | Version | License |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | Apache 2.0 |
| Kotlin Gradle Plugin | 2.0.21 | Apache 2.0 |
| Compose Compiler Plugin | 2.0.21 | Apache 2.0 |
| KSP | 2.0.21-1.0.27 | Apache 2.0 |
| Hilt Gradle Plugin | 2.52 | Apache 2.0 |

---

## How to Verify

To regenerate the dependency graph yourself, run:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

To list every license declared in upstream POMs:

```bash
./gradlew :app:dependenciesReport      # if you add the licensee plugin
```

---

## Compliance Statement

OffScan complies with all upstream license obligations:

1. ✅ **Apache 2.0 dependencies**: copyright notices preserved in `NOTICE`; license text included in this file and `LICENSE`.
2. ✅ **MIT dependencies**: copyright notices preserved in `NOTICE`.
3. ✅ **EPL test dependencies**: not redistributed in APK; test scope only.
4. ✅ **ML Kit (proprietary, free use)**: usage acknowledged in `NOTICE`; no reverse-engineering performed.

If you believe a license obligation has been missed, please open an issue at the project's GitHub repository.
