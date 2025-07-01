/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.plugin

import com.hypherionmc.orion.Constants
import com.hypherionmc.orion.utils.Environment
import com.hypherionmc.orion.utils.GradleUtils
import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.provider.Property
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.util.internal.ConfigureUtil
import java.util.*

/**
 * @author HypherionSA
 *
 * Main Plugin Gradle Extension, to handle setting up things like our Maven Repos, versioning and various
 * external tools
 */
open class OrionExtension(pp: Project) {

    val versioning: Versioning = Versioning()
    val tools: Tools = Tools()
    val jarMerger: JarMerger = JarMerger()
    val enableReleasesMaven: Property<Boolean> = pp.objects.property(Boolean::class.java).convention(false)
    val enableSnapshotsMaven: Property<Boolean> = pp.objects.property(Boolean::class.java).convention(false)
    val enableMirrorMaven: Property<Boolean> = pp.objects.property(Boolean::class.java).convention(false)
    val multiProject: Property<Boolean> = pp.objects.property(Boolean::class.java).convention(false)
    val dopplerToken: Property<String> = pp.objects.property(String::class.java).convention("INVALID")
    val project: Project = pp

    init {
        // Build the version number for the project
        if (project.hasProperty("version_major"))
            versioning.major(Integer.parseInt(project.properties["version_major"].toString()))

        if (project.hasProperty("version_minor"))
            versioning.minor(Integer.parseInt(project.properties["version_minor"].toString()))

        if (project.hasProperty("version_patch"))
            versioning.patch(Integer.parseInt(project.properties["version_patch"].toString()))

        if (project.hasProperty("release") && project.properties["release"].toString().equals("true", ignoreCase = true))
            versioning.uploadBuild(true)

        if (Environment.getenv("BUILD_NUMBER") != null)
            versioning.build(Integer.parseInt(Environment.getenv("BUILD_NUMBER")))

        if (project.hasProperty("version_build"))
            versioning.build(Integer.parseInt(project.properties["version_build"].toString()))
    }

    /**
     * Configure the version generator for the plugin
     *
     * @param action The DSL of the versioning
     */
    fun versioning(action: Action<Versioning>) {
        action.execute(versioning)
    }

    fun jarMerger(action: Action<JarMerger>) {
        action.execute(jarMerger)
    }

    /**
     * Configure the included tools for the plugin
     *
     * @param action The DSL of the tools
     */
    fun tools(action: Action<Tools>) {
        action.execute(tools)
    }

    /**
     * Helper method to force the plugin to configure and apply everything early (Kotlin)
     */
    fun setup(configure: OrionExtension.() -> Unit) {
        this.configure()
        postConfiguration()
    }

    /**
     * Helper method to force the plugin to configure and apply everything early
     */
    fun setup(@DelegatesTo(value = OrionExtension::class, strategy = Closure.DELEGATE_FIRST) closure: Closure<OrionExtension>) {
        println("Configuring ${Constants.ORION_VERSION} to ${Constants.ORION_VERSION}")
        ConfigureUtil.configure(closure, this)
        postConfiguration()
    }

    /**
     * Do plugin configuration as soon as orion.setup is called
     */
    private fun postConfiguration() {
        GradleUtils.configureProject(project, this)
    }

    /**
     * Get an Environment Variable value from either Doppler or the system variables
     *
     * @param key The Environment Key to get the value of
     */
    fun getenv(key: String): String? {
        return System.getenv(key)
    }

    /**
     * Configure Maven for publishing. This defaults to releases, or snapshots for porting/snapshot builds
     *
     * @return The configured maven repository
     */
    fun getPublishingMaven(): Action<MavenArtifactRepository> {
        return Action { mavenArtifactRepository: MavenArtifactRepository ->
            mavenArtifactRepository.setUrl(
                if (!versioning.identifier.equals("release", ignoreCase = true)) Constants.MAVEN_SNAPSHOT_URL else Constants.MAVEN_URL
            )

            mavenArtifactRepository.credentials { c: PasswordCredentials ->
                c.username = Environment.getenv("MAVEN_USER")
                c.password = Environment.getenv("MAVEN_PASS")
            }
        }
    }

    /**
     * Helper method for Kotlin Build Scripts, to get the value of Project Properties without defining each of them
     *
     * @param key The property to try and get the value of
     * @return The property value
     */
    fun getProperty(key: String): String {
        return Optional
            .ofNullable(project.findProperty(key))
            .map { o -> o.toString() }
            .orElseThrow { RuntimeException("Property $key is missing")}
    }

    /**
     * Versioning DSL extension
     */
    open class Versioning {

        // Default to 1.0.0 release
        var major: Int = 1
        var minor: Int = 0
        var patch: Int = 0
        var build: Int = 0
        var identifier: String = "release"
        var isUploadBuild: Boolean = false

        /**
         * Manually configure the MAJOR version value
         *
         * @param major In semver format, for example 1
         */
        fun major(major: Int) {
            this.major = major
        }

        /**
         * Manually configure the MINOR version value
         *
         * @param minor In semver format, for example 1
         */
        fun minor(minor: Int) {
            this.minor = minor
        }

        /**
         * Manually configure the PATCH version value
         *
         * @param patch In semver format, for example 1
         */
        fun patch(patch: Int) {
            this.patch = patch
        }

        /**
         * Manually configure the BUILD version value
         *
         * @param build In semver format, for example 1
         */
        fun build(build: Int) {
            this.build = build
        }

        /**
         * Toggle the IDENTIFIER value being present in the version number
         *
         * @param value Should the identifier be present in the version
         */
        fun uploadBuild(value: Boolean) {
            this.isUploadBuild = value
        }

        /**
         * Set the release identifier. For example, porting, snapshots, etc
         *
         * @param value The identifier. Defaults to release, or snapshots when on CI
         */
        fun identifier(value: String) {
            this.identifier = value
        }

        /**
         * Build a version string to be used inside gradle.
         * This takes into account the CI build number, if one is present
         *
         * @return Semver Version. For example: 1.0.0+port.1
         */
        fun buildVersion(): String {
            var v = "${major}.${minor}.${patch}"

            if (!isUploadBuild) {
                v += "+${identifier}.${build}"
            }

            return v
        }

    }

    /**
     * Tools DSL extension
     */
    open class Tools {

        var enableLombok: Boolean = false
        var enableAutoService: Boolean = false
        var enableNoLoader: Boolean = false
        var enableProcessors: Boolean = false

        /**
         * Enable the Orion Source Code Processors
         */
        fun processors() {
            enableProcessors = true
        }

        /**
         * Enable Lombok
         */
        fun lombok() {
            enableLombok = true
        }

        /**
         * Enable Google Auto Service
         */
        fun autoService() {
            enableAutoService = true
        }

        /**
         * Enable Dummy Modloader code for Nojang Powered Projects
         */
        fun noLoader() {
            enableNoLoader = true
        }
    }

    open class JarMerger {

        var enabled = false
        val inputs = mutableMapOf<String, Pair<String, Any>>()
        var outputJarName: String? = null
        val scanExclude = mutableSetOf<String>()

        fun enable() {
            enabled = true
        }

        fun outputJarName(name: String) {
            outputJarName = name
        }

        fun excludeFromScan(input: List<String>) {
            scanExclude.addAll(input)
        }

        fun neoforge(input: Any, projectName: String = "NeoForge") {
            inputs.put("neoforge", Pair(projectName, input))
        }

        fun forge(input: Any, projectName: String = "Forge") {
            inputs.put("forge", Pair(projectName, input))
        }

        fun quilt(input: Any, projectName: String = "Quilt") {
            inputs.put("quilt", Pair(projectName, input))
        }

        fun fabric(input: Any, projectName: String = "Fabric") {
            inputs.put("fabric", Pair(projectName, input))
        }
    }

}