# Keep ARCore native bridging
-keep class com.google.ar.core.** { *; }
-keepclassmembers class com.google.ar.core.** { *; }

# Keep moshi-generated adapters
-keep class **JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
