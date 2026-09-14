# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Keep serializable classes
-keep class com.denggl2.mason.llm.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# PDFBox treats the JPEG2000 decoder as optional. PDF pages without extractable
# text still fall back to Android PdfRenderer and the configured vision model.
-dontwarn com.gemalto.jp2.JP2Decoder
