import godot.gradle.GodotLanguage
import godot.annotation.processor.classgraph.AnnotationProcessingMode

plugins {
    id("com.utopia-rise.godot-jvm") version "1.0.0-rc2"
}

repositories {
    mavenCentral()
    google()
}

godot {
    languages.set(setOf(GodotLanguage.KOTLIN))
    isGodotCoroutinesEnabled.set(true)
    registration.annotationProcessingMode.set(AnnotationProcessingMode.Inferred)
}

kotlin.sourceSets.main {
    kotlin.srcDirs("demo")
}
