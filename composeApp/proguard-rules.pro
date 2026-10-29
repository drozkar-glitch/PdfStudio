# Pravidla R8 a ProGuard pro projekt PdfStudio (cz.mares.pdfstudio)
-keep class cz.mares.pdfstudio.MainActivity { *; }
-keep class cz.mares.pdfstudio.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn kotlinx.**
-dontwarn org.jetbrains.**
