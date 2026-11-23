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
-optimizationpasses 5
-keep class statusbar.lyric.hook.MainHook { <init>(); }
-adaptresourcefilecontents META-INF/xposed/java_init.list
# SuperLyricApi 4.x compiles against a hidden-api stub; the real class is in the framework
-dontwarn android.os.ServiceManager
# SuperLyricApi 4.0 decodes its wire structs reflectively (no-arg constructor + enum names),
# but its consumer rules only keep the @Wire fields; R8 then drops the constructors here.
-keep class com.hchen.superlyricapi.model.** { *; }
-keep class com.hchen.superlyricapi.wire.Frame { *; }
-keep enum com.hchen.superlyricapi.wire.Frame$Kind { *; }
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable
-repackageclasses ztlgc
-obfuscationdictionary proguard-dic-6.txt
-classobfuscationdictionary proguard-dic-6.txt
-packageobfuscationdictionary proguard-dic-6.txt
-adaptresourcefilenames proguard-dic-6.txt
-adaptresourcefilecontents proguard-dic-6.txt