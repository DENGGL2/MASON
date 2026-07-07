# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Keep serializable classes
-keep class com.denggl2.mason.llm.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
