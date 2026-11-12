-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.arsnova.**$$serializer { *; }
-keepclassmembers class com.arsnova.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-dontwarn com.google.protobuf.**
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**

# JLaTeXMath carga fuentes y símbolos por nombre
-keep class ru.noties.jlatexmath.** { *; }
-keep class org.scilab.forge.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**
