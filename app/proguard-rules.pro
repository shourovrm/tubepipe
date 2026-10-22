# Release shrinking rules. Kept rules come from PipePipeClient app/proguard-rules.pro.

# Readable stack traces for bug reports; the source is public (GPL-3.0), so obfuscation buys nothing.
-dontobfuscate

# The extractor loads its time-ago patterns by class name.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

# OkHttp loads this resource by relative path, so the package name must survive.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# The extractor compiles against org.json but only catches its exception type.
-keep class org.json.JSONException { *; }
