# ============================================================
# Meshi — ProGuard / R8 keep rules
# ============================================================

# Keep generic signatures and annotations (needed by Room, Kotlin reflection,
# and coroutines).
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ------------------------------------------------------------
# Kotlin
# ------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Coroutines internal classes accessed reflectively.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ------------------------------------------------------------
# Room
# ------------------------------------------------------------
# Room generates implementation classes; keep entities, DAOs, and the database.
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.paging.**

# Keep Meshi database entities and their fields (serialised by Room via reflection).
-keep class com.meshi.code.db.** { *; }

# ------------------------------------------------------------
# Meshi protocol & domain models
# ------------------------------------------------------------
# Protocol frames and domain models are parsed/constructed from byte buffers
# and used across coroutine boundaries; keep them intact to avoid R8 stripping
# fields that look unused but are read via destructuring or copy().
-keep class com.meshi.code.protocol.** { *; }
-keep class com.meshi.code.repository.ChatMessage { *; }
-keep class com.meshi.code.repository.AdvertEvent { *; }

# ------------------------------------------------------------
# osmdroid (map tiles)
# ------------------------------------------------------------
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ------------------------------------------------------------
# ZXing (QR encode/decode)
# ------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ------------------------------------------------------------
# AndroidX / Compose (handled largely by the libraries' own consumer rules,
# but silence known warnings).
# ------------------------------------------------------------
-dontwarn javax.annotation.**
