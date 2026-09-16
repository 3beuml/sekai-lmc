# kotlinx.serialization 生成的序列化器需要保留
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.pjsk.toolbox.** {
    *** Companion;
}
-keepclasseswithmembers class com.pjsk.toolbox.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
