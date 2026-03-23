pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://clojars.org/repo") }
    }
}

rootProject.name = "ceilingbounce"
include(":app")

// ---------------------------------------------------------------------------
// Auto-clone helpers
// ---------------------------------------------------------------------------

/**
 * Returns true if [dir] is a shallow git clone (created by our --depth 1
 * auto-clone).  Full user working copies are never shallow, so this
 * distinguishes "managed by settings.gradle.kts" from "user checkout".
 */
fun isShallowClone(dir: java.io.File): Boolean =
    java.io.File(dir, ".git/shallow").isFile

/**
 * Ensure a sibling repository from the clj-android GitHub organization is
 * available for use as an included build.
 *
 * - Missing or empty directories are cloned automatically (--depth 1).
 * - Shallow clones (from a previous auto-clone) are updated with git pull
 *   so they stay current when upstream pushes new commits.
 * - Full (non-shallow) user checkouts are left untouched.
 *
 * Returns the directory if it contains a valid Gradle project, or null if
 * cloning failed (in which case Gradle falls back to published artifacts
 * from mavenLocal / remote repositories).
 */
fun ensureSibling(parent: java.io.File, name: String): java.io.File? {
    val dir = java.io.File(parent, name)
    val hasBuildFile = java.io.File(dir, "build.gradle.kts").isFile

    if (hasBuildFile && isShallowClone(dir)) {
        // Update our own auto-clone to pick up new commits.
        try {
            val proc = ProcessBuilder("git", "pull")
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().forEachLine { /* discard */ }
            proc.waitFor()
        } catch (_: Exception) {
            // Non-fatal — the existing checkout is still usable.
        }
        return dir
    }

    if (hasBuildFile) return dir   // Full user checkout — use as-is.

    // Missing, empty, or corrupt directory — (re-)clone from GitHub.
    println("Cloning $name from https://github.com/clj-android/$name.git ...")
    return try {
        if (dir.isDirectory) dir.deleteRecursively()
        val proc = ProcessBuilder(
            "git", "clone", "--depth", "1",
            "https://github.com/clj-android/$name.git",
            dir.absolutePath
        ).redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().forEachLine { println("  $it") }
        if (proc.waitFor() == 0) dir else {
            println("WARNING: failed to clone $name — falling back to published artifacts")
            null
        }
    } catch (_: Exception) {
        println("WARNING: could not clone $name — falling back to published artifacts")
        null
    }
}

// ---------------------------------------------------------------------------
// Include sibling builds
// ---------------------------------------------------------------------------

// Gradle plugin (must be in pluginManagement to resolve plugin IDs).
ensureSibling(file(".."), "android-clojure-plugin")?.let {
    pluginManagement { includeBuild(it) }
}

// Library dependencies — built from source when available.
listOf("neko", "runtime-core", "runtime-repl", "clojure-patched").forEach { name ->
    ensureSibling(file(".."), name)?.let { includeBuild(it) }
}
