# The app has no reflection-based serialization, so default R8 rules are enough.
# Keep line numbers so Play Console crash reports stay readable after deobfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager instantiates Workers by class name via reflection.
-keep class com.allvideodownloader.app.work.** { *; }
