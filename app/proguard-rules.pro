# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepattributes Signature

# Keep Generic signatures for `typeOf<T>` usage
-keep,allowobfuscation class top.ltfan.knowmad.agent.ChatAgentData

# Keep CalendarState constructor for reflection usage
-keep,allowobfuscation class com.kizitonwose.calendar.compose.CalendarState {
    public <init>(...);
}
-keep,allowobfuscation class com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState {
    public <init>(...);
}

# First found in io.ktor.util.debug.IntellijIdeaDebugDetector
-dontwarn java.lang.management.**
