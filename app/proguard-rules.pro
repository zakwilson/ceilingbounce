# ---- Clojure on Android: R8 keep rules ----------------------------------
#
# Clojure resolves classes reflectively by name everywhere (RT.classForName,
# Compiler.compile, var metadata). Without these rules R8 will strip AOT
# output that looks dead and the app will NoClassDefFoundError at startup.

# Clojure runtime internals.
-keep class clojure.lang.** { *; }
-keep class clojure.java.api.** { *; }
-keep class clojure.core.** { *; }
-dontwarn clojure.**

# Our runtime shim.
-keep class com.goodanser.clj_android.runtime.** { *; }

# neko — uses reflection for Android interop.
-keep class neko.** { *; }
-dontwarn neko.**

# Every Clojure namespace AOT-compiles to an `__init` class that is loaded
# by name via RT.classForName. These MUST be kept. Compiled fn classes are
# reachable from the __init's constants table, so standard tree-shaking
# picks them up once __init is rooted.
-keep class **__init { *; }
-keepclassmembers class ** {
    public static final clojure.lang.Var const__*;
}

# Your application namespaces — keep anything loaded by name from Java.
# Add one line per namespace ClojureApp.loadNamespaces(...) references.
# Munging rule: dots → slashes, hyphens → underscores.
# Example: com.example.my-app.core  →  com/example/my_app/core__init
-keep class com.example.clojuredroid.core__init { *; }
# ...etc

# Patched-Clojure optional holder probed reflectively on older APIs.
-keep class clojure.lang.RT$DesugarPrefixes { *; }
-dontwarn clojure.lang.RT$DesugarPrefixes

# Desugar shims.
-dontwarn j$.**

# Android framework classes that Clojure AOT may reference but aren't on
# the build classpath.
-dontwarn java.lang.management.**
-dontwarn java.beans.**
-dontwarn javax.**
-dontwarn sun.**
