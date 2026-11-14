# kotlinx.serialization : conserver les sérialiseurs générés (utilisés pour le schéma de mise en page).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.homeglass.app.**$$serializer { *; }
-keepclassmembers class com.homeglass.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.homeglass.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
