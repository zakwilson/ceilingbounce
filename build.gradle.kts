// Top-level build file
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.goodanser.clj-android.android-clojure") version "0.5.0-SNAPSHOT" apply false
}

// When Android libraries are consumed via composite builds (includeBuild),
// Gradle sees multiple sub-variants per configuration (android-classes-jar,
// android-lint, jar, r-class-jar, etc.) and cannot choose between them.
// This disambiguation rule tells Gradle to prefer the classes JAR, which
// is the primary artifact needed for compilation and packaging.
// When Android libraries are consumed via composite builds (includeBuild),
// Gradle sees multiple sub-variants per configuration and cannot choose
// between them.  The "jar" artifact type is the full JAR containing both
// compiled classes *and* Java resources (Clojure .clj files), which the
// AOT compiler needs on its classpath.
abstract class PreferJar : AttributeDisambiguationRule<String> {
    override fun execute(details: MultipleCandidatesDetails<String>) {
        if ("jar" in details.candidateValues) {
            details.closestMatch("jar")
        }
    }
}

allprojects {
    dependencies {
        attributesSchema {
            attribute(Attribute.of("artifactType", String::class.java)) {
                disambiguationRules.add(PreferJar::class.java)
            }
        }
    }
}
