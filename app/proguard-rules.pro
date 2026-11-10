# The messaging engine is Go, reached through gomobile's generated Java classes and JNI.
# Keep all of it, with its names, so the native side can find what it calls.
-keep class go.** { *; }
-keep class com.jpetrydesign.pyng.core.** { *; }
-keep interface com.jpetrydesign.pyng.core.** { *; }
-keepclassmembers class * implements com.jpetrydesign.pyng.core.** { *; }

# Stack traces in the debug log stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
