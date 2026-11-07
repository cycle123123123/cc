# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.jmreader.** {
    *** Companion;
}
-keepclasseswithmembers class com.jmreader.** {
    kotlinx.serialization.KSerializer serializer(...);
}
