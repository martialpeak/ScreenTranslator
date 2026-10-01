# ScreenTranslator ProGuard/R8 Rules
# حفاظت از الگوهای API Key از کشف شدن

# MARK: - Keep API key detection safe
-keep class com.screenreader.translator.data.local.AppPreferences { *; }
-keepclassmembers class com.screenreader.translator.data.local.AppPreferences {
    *** *api*;
    *** *key*;
}

# MARK: - Keep Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# MARK: - Keep Serializable classes (JSON/Gson)
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# MARK: - Keep Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.JsonDeserializer
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.TypeAdapter

# MARK: - Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# MARK: - Keep ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# MARK: - Keep TextToSpeech
-keep class android.speech.tts.** { *; }

# MARK: - Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclasseswithmembernames class kotlinx.coroutines.** {
    native <methods>;
}

# MARK: - Logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# MARK: - Remove unused code but keep public API
-dontnote **
-dontwarn **
