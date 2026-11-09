# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class studio.pyreels.**$$serializer { *; }
-keepclassmembers class studio.pyreels.** { *** Companion; }
-keepclasseswithmembers class studio.pyreels.** { kotlinx.serialization.KSerializer serializer(...); }
# bundled data files read with getResourceAsStream
-keepdirectories pyreels/**
-keep class studio.pyreels.core.Resources { *; }
# Chaquopy
-keep class com.chaquo.python.** { *; }
