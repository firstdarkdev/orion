package com.hypherionmc.orion.meta

data class JarInJarMetadata(
    val jars: List<JarEntry>
)

data class JarEntry(
    val identifier: JarIdentifier,
    val version: JarVersion,
    val path: String,
    val isObfuscated: Boolean,
)

data class JarIdentifier(
    val group: String,
    val artifact: String,
)

data class JarVersion(
    val range: String,
    val artifactVersion: String,
)
