import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.0"
    id("java")
    id("idea")
    id("com.gradleup.shadow") version "9.2.0"
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.diffplug.spotless") version "6.13.0"
}
apply(plugin = "maven-publish")

kotlin {
    jvmToolchain(17)
}

val shadeMe by configurations.creating

configurations {
    implementation {
        extendsFrom(shadeMe)
    }
}

val version_major: String by project
val version_minor: String by project
val version_patch: String by project
val okhttp: String by project
val gson: String by project
val diffpatch: String by project
val jgit: String by project
val commons_io: String by project
val lombok: String by project

group = "com.hypherionmc.modutils"
version = "$version_major.$version_minor.$version_patch"
description = "Gradle Utilities for First Dark Development"

repositories {
    mavenCentral()
    maven("https://mcentral.firstdark.dev/releases")
    maven("https://maven.firstdark.dev/releases")
    maven("https://maven.firstdark.dev/snapshots")
    maven("https://maven.covers1624.net")
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())

    shadeMe("com.squareup.okhttp3:okhttp:${okhttp}")
    shadeMe("com.google.code.gson:gson:${gson}")
    shadeMe("codechicken:DiffPatch:${diffpatch}:all")
    shadeMe("org.apache.commons:commons-compress:1.26.2")
    shadeMe("org.eclipse.jgit:org.eclipse.jgit:${jgit}")
    shadeMe("commons-io:commons-io:${commons_io}")
    shadeMe("com.github.javaparser:javaparser-core:3.24.0")
    shadeMe("com.hypherionmc:jarmanager:1.0.5")
    shadeMe("org.tomlj:tomlj:1.1.1")
    shadeMe("org.apache.maven:maven-artifact:4.0.0-rc-3")

    // Lombok
    compileOnly("org.projectlombok:lombok:${lombok}")
    annotationProcessor("org.projectlombok:lombok:${lombok}")
    testCompileOnly("org.projectlombok:lombok:${lombok}")
    testAnnotationProcessor("org.projectlombok:lombok:${lombok}")

    // Unimined
    compileOnly("xyz.wagyourtail.unimined:unimined:1.4.2-SNAPSHOT")
    compileOnly("xyz.wagyourtail.unimined.mapping:unimined-mapping-library-jvm:1.2.1")
    compileOnly("com.gradleup.shadow:shadow-gradle-plugin:9.2.0")
}

tasks.named<ShadowJar>("shadowJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    configurations = listOf(shadeMe)
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Implementation-Version"] = project.version.toString()
    }
}

gradlePlugin {
    plugins {
        create("orionPlugin") {
            id = "com.hypherionmc.modutils.orion"
            description = project.description
            displayName = "Orion"
            version = project.version
            implementationClass = "com.hypherionmc.orion.plugin.OrionPlugin"
            tags.set(listOf("gradle", "utils", "fdd"))
        }

        create("orionPortingPlugin") {
            id = "com.hypherionmc.modutils.orion.porting"
            description = project.description
            displayName = "OrionPorting"
            version = project.version
            implementationClass = "com.hypherionmc.orion.plugin.porting.OrionPortingPlugin"
            tags.set(listOf("gradle", "utils", "fdd"))
        }

        create("orionMultiminedPlugin") {
            id = "com.hypherionmc.modutils.orion.multimined"
            description = project.description
            displayName = "OrionMultimined"
            version = project.version
            implementationClass = "com.hypherionmc.orion.plugin.multimined.MultiminedPlugin"
            tags.set(listOf("gradle", "utils", "fdd"))
        }

        create("origamiPlugin") {
            id = "com.hypherionmc.modutils.orion.origami"
            description = project.description
            displayName = "OrionOrigami"
            version = project.version
            implementationClass = "com.hypherionmc.orion.plugin.paper.OrigamiPlugin"
            tags.set(listOf("gradle", "utils", "fdd"))
        }
    }
}

spotless {
    kotlin {
        targetExclude("src/test/**")
        licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
    }
}

publishing {
    repositories {
        maven {
            url = uri(System.getenv("MAVEN_URL"))
            credentials {
                username = System.getenv("MAVEN_USER")
                password = System.getenv("MAVEN_PASS")
            }
        }
    }
}
