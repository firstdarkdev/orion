package com.hypherionmc.orion.meta

data class MultiFabricMeta(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val depends: Map<String, String>,
    val jars: List<NestedJar>,
    val custom: CustomData
)

data class NestedJar(
    val file: String
)

data class CustomData(
    val modmenu: ModMenuData
)

data class ModMenuData(
    val badges: List<String>,
    val parent: ModMenuParent
)

data class ModMenuParent(
    val id: String
)