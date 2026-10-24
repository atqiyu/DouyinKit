# Keep Kotlin serialization metadata and generated serializers.
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.atqiyu.douyinkit.**$$serializer { *; }
-keepclassmembers class com.atqiyu.douyinkit.** {
    *** Companion;
}
-keepclasseswithmembers class com.atqiyu.douyinkit.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JavaScript bridge fallback: methods are invoked by name from WebView.
-keepclassmembers class com.atqiyu.douyinkit.parser.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp / Conscrypt
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
