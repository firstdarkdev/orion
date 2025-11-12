package com.hypherionmc.orion.task.paper

import com.hypherionmc.orion.plugin.paper.OrigamiExtension
import okio.IOException
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction

open class AfterCompileTask: DefaultTask() {

    @TaskAction
    @Throws(IOException::class)
    fun cleanupSourcesTask() {
        // Check that the extension is registered
        val extension = project.extensions.getByType(OrigamiExtension::class.java)
            ?: throw GradleException("Cannot find origami extension on project")

        val sourceSetName = extension.sourceSet.get()
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val sourceSet = sourceSets.getByName(sourceSetName)

        val markerFile = project.layout.buildDirectory.dir("processedSources/main/marker.txt").get().asFile
        val toDelete = FileUtils.readLines(markerFile, "UTF-8")

        if (toDelete.isNotEmpty()) {
            toDelete.forEach {
                val fileToDelete = sourceSet.output.classesDirs.first().resolve(it.replace(".java", ".class"))
                val resourceFile = sourceSets.named("main").get().output.resourcesDir?.resolve(it)

                if (fileToDelete.exists()) {
                    project.logger.lifecycle("Deleted: $fileToDelete")
                    FileUtils.deleteQuietly(fileToDelete)
                }

                if (resourceFile != null && resourceFile.exists()) {
                    project.logger.lifecycle("Deleted: $resourceFile")
                    FileUtils.deleteQuietly(resourceFile)
                }
            }
        }

        project.logger.lifecycle("⚡ Finished preparing Paper Sources")
    }

}