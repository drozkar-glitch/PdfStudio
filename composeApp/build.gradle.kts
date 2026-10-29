// Konfigurace modulu composeApp pro PdfStudio (Android SDK 36 + iOS + R8)
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Automaticka kontrola a odstraneni cizich slozek pred sestavenim projektu PdfStudio
listOf("commonMain", "androidMain", "iosMain").forEach { srcName ->
    val baseKotlinDir = project.file("src/$srcName/kotlin")
    if (baseKotlinDir.exists()) {
        baseKotlinDir.listFiles()?.forEach { rootPkg ->
            if (rootPkg.name != "cz") {
                rootPkg.deleteRecursively()
            }
        }
        val czFolder = File(baseKotlinDir, "cz")
        if (czFolder.exists()) {
            czFolder.listFiles()?.forEach { subPkg ->
                if (subPkg.name != "mares") {
                    subPkg.deleteRecursively()
                }
            }
            val maresFolder = File(czFolder, "mares")
            if (maresFolder.exists()) {
                maresFolder.listFiles()?.forEach { projectPkg ->
                    if (projectPkg.name != "pdfstudio") {
                        projectPkg.deleteRecursively()
                    }
                }
            }
        }
    }
}

listOf(
    "src/commonMain/kotlin/cz/mares/pdfstudio/App.kt",
    "src/commonMain/kotlin/cz/mares/pdfstudio/platform/PlatformPdfBridge.kt",
    "src/androidMain/kotlin/cz/mares/pdfstudio/platform/PlatformPdfBridge.android.kt",
    "src/androidMain/kotlin/cz/mares/pdfstudio/platform/PlatformPdfBridge.kt",
    "src/iosMain/kotlin/cz/mares/pdfstudio/platform/PlatformPdfBridge.ios.kt",
    "src/iosMain/kotlin/cz/mares/pdfstudio/platform/PlatformPdfBridge.kt"
).forEach { legacyFileRelPath ->
    val legacyFile = project.file(legacyFileRelPath)
    if (legacyFile.exists()) {
        legacyFile.delete()
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { targetIos ->
        targetIos.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += listOf("-Xbinary=bundleId=cz.mares.pdfstudio")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.activity:activity-compose:1.9.2")
        }
    }
}

val rawBuild = System.getenv("PROJECT_BUILD_NUMBER")?.toIntOrNull()
    ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    ?: 1
val ciBuildNumber = if (rawBuild < 100) rawBuild + 100 else rawBuild

android {
    namespace = "cz.mares.pdfstudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.pdfstudio"
        minSdk = 24
        targetSdk = 36
        versionCode = ciBuildNumber
        versionName = "1.0.$ciBuildNumber"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("CM_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystorePath = System.getenv("CM_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val generateIosIcon = tasks.register("generateIosIcon") {
    doLast {
        val assetDir = rootProject.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset")
        if (!assetDir.exists()) {
            assetDir.mkdirs()
        }
        val outPngFile = File(assetDir, "app-icon-1024.png")
        val iconSide = 1024

        fun writeChunk(stream: DataOutputStream, typeTag: String, payload: ByteArray) {
            stream.writeInt(payload.size)
            val tagBytes = typeTag.toByteArray(Charsets.US_ASCII)
            stream.write(tagBytes)
            stream.write(payload)
            val crcCalc = CRC32()
            crcCalc.update(tagBytes)
            crcCalc.update(payload)
            stream.writeInt(crcCalc.value.toInt())
        }

        FileOutputStream(outPngFile).use { fileOut ->
            val dataOut = DataOutputStream(fileOut)
            dataOut.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

            val headerBytes = ByteArrayOutputStream()
            DataOutputStream(headerBytes).apply {
                writeInt(iconSide)
                writeInt(iconSide)
                writeByte(8)
                writeByte(2)
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
            writeChunk(dataOut, "IHDR", headerBytes.toByteArray())

            val pixelBytes = ByteArrayOutputStream()
            DeflaterOutputStream(pixelBytes, Deflater(Deflater.BEST_SPEED)).use { compressedOut ->
                val scanline = ByteArray(1 + iconSide * 3)
                for (py in 0 until iconSide) {
                    scanline[0] = 0
                    var pos = 1
                    for (px in 0 until iconSide) {
                        val insidePaper = px in 220..804 && py in 140..884
                        val insideTopRed = px in 220..804 && py in 140..310
                        val insideRowA = px in 300..720 && py in 420..465
                        val insideRowB = px in 300..720 && py in 530..575
                        val insideRowC = px in 300..580 && py in 640..685

                        val redVal: Int
                        val greenVal: Int
                        val blueVal: Int

                        if (insideTopRed) {
                            redVal = 211; greenVal = 47; blueVal = 47
                        } else if (insideRowA || insideRowB || insideRowC) {
                            redVal = 30; greenVal = 64; blueVal = 175
                        } else if (insidePaper) {
                            redVal = 255; greenVal = 255; blueVal = 255
                        } else {
                            redVal = 20; greenVal = 50; blueVal = 140
                        }

                        scanline[pos++] = redVal.toByte()
                        scanline[pos++] = greenVal.toByte()
                        scanline[pos++] = blueVal.toByte()
                    }
                    compressedOut.write(scanline)
                }
            }
            writeChunk(dataOut, "IDAT", pixelBytes.toByteArray())
            writeChunk(dataOut, "IEND", ByteArray(0))
        }
    }
}

tasks.matching { it.name.startsWith("link") && it.name.contains("FrameworkIos") }.configureEach {
    dependsOn(generateIosIcon)
}
