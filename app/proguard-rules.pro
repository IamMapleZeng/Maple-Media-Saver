# Reflectively loaded by LSPosed; preserve names and public no-arg construction.
-keep class io.github.iammaplezeng.maplemediasaver.XTGMediaSaverModule { public <init>(); public *; }
-keep class io.github.iammaplezeng.maplemediasaver.ModuleStatus { public static boolean isActivated(); }
-keep class org.schabi.newpipe.extractor.timeago.** { *; }
-keep class org.mozilla.javascript.** { *; }
