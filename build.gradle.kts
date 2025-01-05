// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("com.android.library") version "8.2.0" apply false
    id("com.google.dagger.hilt.android") version "2.44" apply false // hilt dagger
}

buildscript {
    repositories {
        google()         // Make sure Google repository is included
        mavenCentral()   // Make sure Maven Central repository is included
        maven("https://jitpack.io")
    }
    dependencies {
        // Add the Hilt classpath dependency here
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.44")  // Use the latest stable version of Hilt
    }
}
true // Needed to make the Suppress annotation work for the plugins block