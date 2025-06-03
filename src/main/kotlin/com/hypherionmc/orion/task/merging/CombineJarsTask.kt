/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task.merging

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hypherionmc.jarmanager.JarManager
import com.hypherionmc.jarrelocator.Relocation
import com.hypherionmc.orion.Constants
import com.hypherionmc.orion.meta.*
import com.hypherionmc.orion.plugin.OrionExtension
import com.hypherionmc.orion.utils.FileTools
import org.apache.commons.io.FileUtils
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.gradle.api.GradleException
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.internal.file.copy.CopyActionProcessingStream
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.bundling.Jar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.stream.Collectors

open class CombineJarsTask: Jar() {

    companion object {
        val hasRun: AtomicBoolean = AtomicBoolean(false)
        val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    init {
        inputs.files()
        outputs.file(File(project.rootProject.layout.buildDirectory.get().asFile, "libs/${archiveFileName.get()}"))
        outputs.upToDateWhen { hasRun.get() }
    }

    @Throws(IOException::class)
    fun combineJars() {
        val extension = project.extensions.getByType(OrionExtension::class.java)
            ?: throw GradleException("Cannot find orion extension on project")

        logger.lifecycle("⚡ Start Combining Jars")
        // Jar manager to pack and unpack jars
        val jarManager = JarManager.getInstance()

        // Main, temp working directory
        val workingDir = File(project.rootProject.layout.buildDirectory.asFile.get(), "orion-working")
        if (workingDir.exists()) FileUtils.deleteDirectory(workingDir)
        workingDir.mkdirs()

        // List of directories being used during the output jar building process
        val workingDirs = mutableListOf<File>()

        // The Shared Library jar, containing duplicate classes and assets
        val sharedLibraryDir = File(workingDir, "shared")
        sharedLibraryDir.mkdirs()

        // Process Projects and Inputs
        extension.jarMerger.inputs.forEach { input ->
            val platform = input.key
            val projectName = input.value.first
            val artifact = input.value.second
            val file = FileTools.resolveFile(project.rootProject.project(projectName), artifact)

            if (!file.name.endsWith(".jar"))
                return@forEach

            logger.lifecycle("⚡ Processing ${file.name}")
            // Create the temp directory, and extract the jar
            val jarDir = File(workingDir, platform)
            jarDir.mkdirs()
            jarManager.unpackJar(file, jarDir)

            // Delete the original jar
            FileUtils.delete(file)

            // Add to working directories, for later use
            workingDirs.add(jarDir)
        }

        // Extract duplicate assets, classes and files
        buildSharedLibrary(workingDirs,
            extension.jarMerger.scanExclude.map { it -> File(it) }.toList(),
            sharedLibraryDir
        )

        var hasSharedLibrary = false
        val modMeta = FileTools.readModMetadata(workingDirs)

        // Check if the shared library contains any files. If not, we delete it and won't process it further
        if (sharedLibraryDir.listFiles() == null || sharedLibraryDir.listFiles().isEmpty()) {
            FileUtils.deleteDirectory(sharedLibraryDir)
        } else {
            // Library has files, so we create the necessary metadata so that modloaders can use it
            writeSharedMetadata(sharedLibraryDir, extension, modMeta)
            workingDirs.add(sharedLibraryDir)
            hasSharedLibrary = true
        }

        // Set up the MultiJar working directories
        val multiJarDir = File(workingDir, "multi")
        val multiJarNestDir = File(multiJarDir, "META-INF/orion")
        multiJarNestDir.mkdirs()

        // Versioning for NeoJar
        val mcVersion = project.properties["minecraft_version"] as String
        val aVersion = DefaultArtifactVersion(mcVersion)
        val modernMc = DefaultArtifactVersion("1.20")

        // Download the MultiJar loader from our maven
        val multiJar = File(workingDir, "neojar.jar")
        logger.lifecycle("⚡ Downloading NeoJar Loader Wrapper")
        FileTools.download(if (aVersion < modernMc) Constants.NEOJAR else "${Constants.NEOJAR}:modern", multiJar)

        // Unpack the MultiLoader template jar, to start building
        jarManager.unpackJar(multiJar, multiJarDir)
        FileUtils.delete(multiJar)

        // Repack the now processed directories, into their final jars
        workingDirs.forEach { wkDir ->
            val outputFile = File(multiJarNestDir, "${modMeta.id}-${wkDir.name}-${modMeta.version}.jar")
            jarManager.packJar(wkDir, outputFile)
            FileUtils.deleteDirectory(wkDir)
        }

        val remapPack = mutableListOf<Relocation>()
        remapPack.add(Relocation("com.hypherionmc.neojar", "${modMeta.id}_wrapper"))

        // Write needed service files for neojar
        FileUtils.moveDirectory(File(multiJarDir, "com/hypherionmc/neojar"), File(multiJarDir, "${modMeta.id}_wrapper"))
        FileUtils.write(File(multiJarDir, "META-INF/services/net.minecraftforge.forgespi.locating.IDependencyLocator"),
            "${modMeta.id}_wrapper.ForgeJarInJarLoader", StandardCharsets.UTF_8)
        FileUtils.write(File(multiJarDir, "META-INF/services/net.neoforged.neoforgespi.locating.IDependencyLocator"),
            "${modMeta.id}_wrapper.NeoForgeJarInJarLoader", StandardCharsets.UTF_8)

        logger.lifecycle("⚡ Writing metadata files")
        writeDummyModData(multiJarDir, hasSharedLibrary, extension, modMeta)

        writeSharedMetadata(multiJarDir, extension, ModData(
            modMeta.id,
            modMeta.name,
            modMeta.version,
            false,
            modMeta.license
        ))

        logger.lifecycle("⚡ Packing final jar")
        jarManager.remapAndPack(multiJarDir, archiveFile.get().asFile, remapPack)
        FileUtils.deleteDirectory(workingDir)
        hasRun.set(true)
    }

    @Throws(IOException::class)
    private fun writeDummyModData(workingDir: File, hasSharedLibrary: Boolean, ext: OrionExtension, modMeta: ModData) {
        // We create a fake manifest file for the jar
        val manifest = Manifest()
        val mainAttributes = manifest.mainAttributes
        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        mainAttributes.put(Attributes.Name("Implementation-Version"), ext.versioning.buildVersion())

        // Create the META-INF folder
        val metaDir = File(workingDir, "META-INF")
        metaDir.mkdirs()

        // Write the manifest file
        FileOutputStream(File(metaDir, "MANIFEST.MF")).use { out ->
            manifest.write(out)
        }

        // Create a fake Fabric/Quilt mod entry, so that fabric/quilt can load this.
        if (modMeta.isFabric) {
            val version = if (modMeta.version.isEmpty() || modMeta.version.startsWith("$")) ext.versioning.buildVersion() else modMeta.version
            val jars = mutableListOf(NestedJar("META-INF/orion/${modMeta.id}-fabric-${modMeta.version}.jar"))
            val depends = mutableMapOf<String, String>()
            depends.put(modMeta.id, version)

            if (hasSharedLibrary) {
                jars.add(NestedJar("META-INF/orion/${modMeta.id}-shared-${modMeta.version}.jar"))
                depends.put("${modMeta.id}_shared", version)
            }

            val meta = MultiFabricMeta(
                1,
                "${modMeta.id}_loader",
                "${modMeta.name} Multi Loader",
                version,
                "A utility library, allowing the correct platform for ${modMeta.name} to be loaded by the modloader",
                depends,
                jars,
                CustomData(
                    ModMenuData(
                        listOf("library"),
                        ModMenuParent(modMeta.id)
                    )
                )
            )

            val json = gson.toJson(meta)
            FileUtils.write(File(workingDir, "fabric.mod.json"), json, StandardCharsets.UTF_8)
        }

        // Create the Orion Marker for NeoForge/Forge Mod Loading Logic
        FileUtils.write(File(workingDir, "META-INF/orion/.orion"), "${modMeta.id}|${modMeta.version}|${hasSharedLibrary}", StandardCharsets.UTF_8)
    }

    @Throws(IOException::class)
    private fun writeSharedMetadata(workingDir: File, ext: OrionExtension, modMeta: ModData) {
        // We create a fake manifest file for the jar
        val manifest = Manifest()
        val mainAttributes = manifest.mainAttributes
        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        mainAttributes.put(Attributes.Name("Implementation-Version"), ext.versioning.buildVersion())

        // This is important for Forge and NeoForge, so that it does not complain about the library being an invalid mod.
        // It also tells them NOT to load the library as a mod
        mainAttributes.put(Attributes.Name("FMLModType"), "LIBRARY")

        // Create the META-INF folder
        val metaDir = File(workingDir, "META-INF")
        metaDir.mkdirs()

        // Write the manifest file
        FileOutputStream(File(metaDir, "MANIFEST.MF")).use { out ->
            manifest.write(out)
        }

        if (modMeta.isFabric) {
            val version = if (modMeta.version.isEmpty() || modMeta.version.startsWith("$")) ext.versioning.buildVersion() else modMeta.version

            // Create a fake Fabric/Quilt mod entry, so that fabric/quilt can load this.
            val meta = DummyFabricMeta(
                1,
                "${modMeta.id}_shared",
                "${modMeta.name} Shared Library",
                version,
                "Shared Resources and Libraries for ${modMeta.name}",
                CustomData(
                    ModMenuData(
                        listOf("library"),
                        ModMenuParent(modMeta.id)
                    )
                )
            )

            // Save the metadata
            val json = gson.toJson(meta)
            FileUtils.write(File(workingDir, "fabric.mod.json"), json, StandardCharsets.UTF_8)
        }
    }

    @Throws(Exception::class)
    fun buildSharedLibrary(rootFolders: MutableList<File>, excludedPaths: List<File?>, targetDir: File?) {
        // Hashes created for the files, to confirm they are really duplicates
        val hashMap: MutableMap<String?, MutableList<File?>> = HashMap()

        // Build a list of excluded folders and files, that will not be moved to the shared library
        val excludedRelPaths =
            excludedPaths.stream().map { f: File? -> f.toString().replace(File.separatorChar, '/') }
                .collect(Collectors.toSet())

        // Scan the working directories for duplicate files
        for (root in rootFolders) {
            val files = FileUtils.listFiles(root, null, true)

            for (file in files) {
                // File is excluded, so we do not process it
                if (isExcluded(file, rootFolders, excludedRelPaths)) continue

                // Compute the hash of the file
                val hash: String? = FileTools.hashFile(file)
                hashMap.computeIfAbsent(hash) { _: String? -> ArrayList() }.add(file)
            }
        }

        // Process the duplicates
        for (duplicates in hashMap.values) {
            if (duplicates.size <= 1) continue

            // Below, we move the duplicate files to the shared library, while removing the originals
            val first: File = duplicates[0]!!
            val root: File = FileTools.findRootFolder(first, rootFolders) ?: continue

            val relativePath = root.toPath().relativize(first.toPath())
            val targetFile = File(targetDir, relativePath.toString())
            targetFile.getParentFile().mkdirs()

            FileUtils.moveFile(first, targetFile)

            // Clean up the folders from the processed folders, so that there are no empty folders left behind
            for (i in 1..<duplicates.size) {
                val dup: File = duplicates[i]!!
                FileUtils.forceDelete(dup)
                val rt: File = FileTools.findRootFolder(dup, rootFolders) ?: continue
                FileTools.cleanupEmptyParents(dup, rt)
            }
        }
    }

    @Throws(IOException::class)
    private fun isExcluded(file: File, rootFolders: MutableList<File>, excludedRelativePaths: MutableSet<String?>): Boolean {
        val root: File = FileTools.findRootFolder(file, rootFolders) ?: return false

        val relPath = root.toPath().relativize(file.toPath())
        val normalized = relPath.toString().replace(File.separatorChar, '/')

        for (excluded in excludedRelativePaths) {
            if (normalized == excluded || normalized.startsWith("$excluded/")) {
                return true
            }
        }
        return false
    }

    override fun createCopyAction(): CopyAction {
        return CopyAction { copyActionProcessingStream: CopyActionProcessingStream? ->
            copyActionProcessingStream!!.process { fileCopyDetailsInternal: FileCopyDetailsInternal? ->
                try {
                    if (!hasRun.get())
                        combineJars()
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
            WorkResults.didWork(true)
        }
    }

}