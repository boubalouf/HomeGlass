# SnakeYAML (dépendance de GeckoView) référence l'introspection Java desktop, absente
# d'Android : ces chemins ne sont jamais exécutés sur l'appareil.
-dontwarn java.beans.**

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
