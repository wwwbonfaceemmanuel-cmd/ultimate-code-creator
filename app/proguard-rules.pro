# Room generates implementations referenced by the framework.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn org.json.**

# WebView JavaScript bridges are not used. If one is added later, keep its
# @JavascriptInterface methods explicitly here.
