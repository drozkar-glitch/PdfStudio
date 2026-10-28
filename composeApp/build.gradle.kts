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

// Automaticke vycisteni cizich slozek (napr. cz/taximares) a starych kolidujicich souboru pred kompilaci
listOf("commonMain", "androidMain", "iosMain").forEach { sourceSet ->
    val kotlinDir = project.file("src/$sourceSet/kotlin")
    if (kotlinDir.exists()) {
        kotlinDir.listFiles()?.forEach { topDir ->
            if (topDir.name != "cz") {
                topDir.deleteRecursively()
            }
        }
        val czDir = File(kotlinDir, "cz")
        if (czDir.exists()) {
            czDir.listFiles()?.forEach { secondDir ->
                if (secondDir.name != "mares") {
                    secondDir.deleteRecursively()
                }
            }
            val maresDir = File(czDir, "mares")
            if (maresDir.exists()) {
                maresDir.listFiles()?.forEach { appDir ->
                    if (appDir.name != "pdfstudio") {
                        appDir.deleteRecursively()
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
).forEach { oldPath ->
    val oldFile = project.file(oldPath)
    if (oldFile.exists()) {
        oldFile.delete()
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
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
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

val ciBuildNumber = System.getenv("PROJECT_BUILD_NUMBER")?.toIntOrNull() ?: 1

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
            isMinifyEnabled = false
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
        val iconDir = rootProject.file("iosApp/iosApp/Assets.xcassets/AppIcon.appiconset")
        if (!iconDir.exists()) {
            iconDir.mkdirs()
        }
        val iconFile = File(iconDir, "app-icon-1024.png")
        val width = 1024
        val height = 1024

        fun writePngChunk(out: DataOutputStream, chunkType: String, data: ByteArray) {
            out.writeInt(data.size)
            val typeBytes = chunkType.toByteArray(Charsets.US_ASCII)
            out.write(typeBytes)
            out.write(data)
            val crc = CRC32()
            crc.update(typeBytes)
            crc.update(data)
            out.writeInt(crc.value.toInt())
        }

        FileOutputStream(iconFile).use { fos ->
            val out = DataOutputStream(fos)
            out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

            val ihdrBuf = ByteArrayOutputStream()
            val ihdrOut = DataOutputStream(ihdrBuf)
            ihdrOut.writeInt(width)
            ihdrOut.writeInt(height)
            ihdrOut.writeByte(8)
            ihdrOut.writeByte(2)
            ihdrOut.writeByte(0)
            ihdrOut.writeByte(0)
            ihdrOut.writeByte(0)
            writePngChunk(out, "IHDR", ihdrBuf.toByteArray())

            val idatBuf = ByteArrayOutputStream()
            val deflater = Deflater(Deflater.BEST_SPEED)
            DeflaterOutputStream(idatBuf, deflater).use { dos ->
                val rowBuffer = ByteArray(1 + width * 3)
                for (y in 0 until height) {
                    rowBuffer[0] = 0
                    var idx = 1
                    for (x in 0 until width) {
                        val inSheet = x in 220..804 && y in 140..884
                        val inHeaderBand = x in 220..804 && y in 140..310
                        val inTextLine1 = x in 300..720 && y in 420..465
                        val inTextLine2 = x in 300..720 && y in 530..575
                        val inTextLine3 = x in 300..580 && y in 640..685

                        val r: Int
                        val g: Int
                        val b: Int

                        if (inHeaderBand) {
                            r = 211; g = 47; b = 47
                        } else if (inTextLine1 || inTextLine2 || inTextLine3) {
                            r = 30; g = 64; b = 175
                        } else if (inSheet) {
                            r = 255; g = 255; b = 255
                        } else {
                            r = 20; g = 50; b = 140
                        }

                        rowBuffer[idx++] = r.toByte()
                        rowBuffer[idx++] = g.toByte()
                        rowBuffer[idx++] = b.toByte()
                    }
                    dos.write(rowBuffer)
                }
            }
            writePngChunk(out, "IDAT", idatBuf.toByteArray())
            writePngChunk(out, "IEND", ByteArray(0))
        }
    }
}

tasks.matching { it.name.startsWith("link") && it.name.contains("FrameworkIos") }.configureEach {
    dependsOn(generateIosIcon)
}
