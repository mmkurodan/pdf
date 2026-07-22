# Release uses minifyEnabled true (R8, full mode) + shrinkResources. These keep
# rules cover the reflective / JNI libraries that shrinking would otherwise break.

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
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# kotlinx.serialization: keep Companion + serializer() so JSON (de)serialization of
# the app's @Serializable models survives R8 full mode.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static **$* *;
}

# ONNX Runtime (on-device PaddleOCR): JNI natives + reflective access.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
