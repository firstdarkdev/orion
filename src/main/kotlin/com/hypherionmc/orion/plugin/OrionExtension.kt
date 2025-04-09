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
import org.gradle.util.internal.ConfigureUtil
import java.util.*


open class OrionExtension(pp: Project) {

    val versioning: Versioning = Versioning()
    val tools: Tools = Tools()
    val enableReleasesMaven: Property<Boolean> = pp.objects.property(Boolean::class.java).convention(false)
    val enableSnapshotsMaven: Property<Boolean> = pp.objects.property(Boolean::class.java).convention(false)
    val enableMirrorMaven: Property<Boolean> = pp.objects.property(Boolean::class.java).convention(false)
    val multiProject: Property<Boolean> = pp.objects.property(Boolean::class.java).convention(false)
    val dopplerToken: Property<String> = pp.objects.property(String::class.java).convention("INVALID")
    val project: Project = pp

    init {
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

    fun versioning(action: Action<Versioning>) {
        action.execute(versioning)
    }

    fun tools(action: Action<Tools>) {
        action.execute(tools)
    }

    fun setup(@DelegatesTo(value = OrionExtension::class, strategy = Closure.DELEGATE_FIRST) closure: Closure<OrionExtension>) {
        ConfigureUtil.configure(closure, this)
        postConfiguration()
    }

    private fun postConfiguration() {
        GradleUtils.configureProject(project, this)
    }

    fun getenv(key: String): String? {
        return System.getenv(key)
    }

    fun getPublishingMaven(): Action<out MavenArtifactRepository> {
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

    fun getProperty(key: String): String {
        return Optional
            .ofNullable(project.findProperty(key))
            .map { o -> o.toString() }
            .orElseThrow { RuntimeException("Property $key is missing")}
    }

    open class Versioning {

        var major: Int = 1
        var minor: Int = 0
        var patch: Int = 0
        var build: Int = 0
        var identifier: String = "release"
        var isUploadBuild: Boolean = false

        fun major(major: Int) {
            this.major = major
        }

        fun minor(minor: Int) {
            this.minor = minor
        }

        fun patch(patch: Int) {
            this.patch = patch
        }

        fun build(build: Int) {
            this.build = build
        }

        fun uploadBuild(value: Boolean) {
            this.isUploadBuild = value
        }

        fun identifier(value: String) {
            this.identifier = value
        }

        fun buildVersion(): String {
            var v = "${major}.${minor}.${patch}}"

            if (!isUploadBuild) {
                v += "+${identifier}.${build}"
            }

            return v
        }

    }

    open class Tools {

        var enableLombok: Boolean = false
        var enableAutoService: Boolean = false
        var enableNoLoader: Boolean = false

        fun lombok() {
            enableLombok = true
        }

        fun autoService() {
            enableAutoService = true
        }

        fun noLoader() {
            enableNoLoader = true
        }
    }

}