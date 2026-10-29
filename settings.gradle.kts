// Konfigurace repozitaru a modulu pro projekt PdfStudio (cz.mares.pdfstudio)
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PdfStudio"
include(":composeApp")
