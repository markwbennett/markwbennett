# The Anthropic SDK serializes its request/response models with Jackson, which finds
# fields and constructors reflectively. Keep the model classes and Jackson's own
# annotations if you turn minification on.
-keep class com.anthropic.** { *; }
-keepclassmembers class com.anthropic.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# OkHttp / Okio ship their own rules but reference optional platform pieces.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Pulled in transitively for JSON-schema generation, which this app does not use.
-dontwarn io.swagger.v3.**
-dontwarn jakarta.validation.**
-dontwarn javax.validation.**
