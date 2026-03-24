plugins {
    id("com.android.application")
    id("com.goodanser.clj-android.android-clojure")
}

android {
    namespace = "com.zakreviews.ceilingbounce"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zakreviews.ceilingbounce"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            // Our Android-compatible stubs in runtime-repl shadow nREPL's originals
            pickFirsts += listOf("nrepl/socket.clj", "nrepl/socket/dynamic.clj")
            // google-closure-library and google-closure-library-third-party both contain README.md
            excludes += listOf("README.md")
        }
    }
}

clojureOptions {
    warnOnReflection.set(true)
}

dependencies {
    implementation("org.clojure:clojure:1.12.0")
    implementation("com.goodanser.clj-android:neko:5.0.0-SNAPSHOT")
    implementation("org.clojure:data.csv:0.1.3")
    implementation("org.clojure:core.async:1.8.741")
    implementation("amalloy:ring-buffer:1.3.1")
    implementation("overtone:at-at:1.2.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.androidplot:androidplot-core:1.5.11")
    implementation("com.halfhp.fig:figlib:1.0.11")
}
