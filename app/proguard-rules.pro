# Release uses minifyEnabled false, so these are mostly defensive for anyone
# who flips minification on later.

# PDFBox-Android reflectively touches font/COS classes.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Tesseract JNI bridge.
-keep class com.googlecode.tesseract.** { *; }
-keep class com.googlecode.leptonica.** { *; }
-dontwarn com.googlecode.**

# kotlinx.serialization generated serializers.
-keepclassmembers class **$$serializer { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
