package com.hypherionmc.orion.plugin.multimined

import org.gradle.api.Plugin
import org.gradle.api.Project

class MultiminedPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.create("multimined", MultiMinedExtension::class.java, project)
    }

}