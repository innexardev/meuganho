# kotlinx.serialization: mantém serializers gerados usados no backup.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.innexar.meuganho.** {
    *** Companion;
}
-keepclasseswithmembers class com.innexar.meuganho.** {
    kotlinx.serialization.KSerializer serializer(...);
}
