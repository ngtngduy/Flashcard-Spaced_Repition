# Add project specific ProGuard rules here.
# Keep Room entities and converters
-keep class com.uth.memora.core.database.entity.** { *; }
-keep class com.uth.memora.core.model.** { *; }

# Keep Google Generative AI (Gemini SDK) & Kotlin Serialization classes
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# Keep Firebase Auth, Firestore, Storage & Google Credential Manager classes
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.libraries.identity.googleid.**
