## --- Kotlin / metadata ---
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,allowobfuscation,allowshrinking class kotlin.reflect.jvm.internal.impl.builtins.BuiltInsLoaderImpl

## --- Moshi (used by Retrofit converter) ---
# Keep generated JsonAdapters and Moshi reflection metadata for our DTOs.
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep class **JsonAdapter { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# Our Xtream DTOs are accessed by Moshi reflection.
-keep class com.iptv.app.data.api.** { *; }

## --- Retrofit / OkHttp ---
-keepattributes Exceptions, InnerClasses, Signature
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

## --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp

## --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

## --- ExoPlayer / Media3 ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

## --- AndroidX Security (Tink) ---
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

## --- Compose / runtime classes used reflectively ---
-keepclassmembers class androidx.compose.runtime.** { *; }

## --- WorkManager ---
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
