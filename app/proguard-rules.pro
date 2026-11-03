# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.karelisio.bulle.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
