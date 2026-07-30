-keep class com.tabdeck.app.model.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
