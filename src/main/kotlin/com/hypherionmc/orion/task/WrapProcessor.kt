/*
 * This file is part of orion, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 HypherionSA and Contributors
 *
 */
package com.hypherionmc.orion.task

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.jvm.optionals.getOrNull

/**
 * @author HypherionSA
 *
 * Annotation processing code, to handle processing source files that contain the WrapClass annotation
 */
open class WrapProcessor: DefaultTask() {

    @TaskAction
    fun processWrapSources() {
        project.logger.lifecycle("⚡ Running Annotation Processor")

        // Get the input sources directory, and set up the temp working directory
        val sourceDir = project.projectDir.resolve("src/main")
        val outputDir = project.layout.buildDirectory.dir("generated/wrapped-sources").get().asFile
        if (outputDir.exists()) {
            FileUtils.deleteDirectory(outputDir)
        }
        outputDir.mkdirs()

        // We only want to process java files (for now)
        val sourceFiles = sourceDir.walkTopDown().filter { it.isFile && (it.extension == "java" ) }

        for (file in sourceFiles) {
            // Read the raw source code
            val content = file.readText(StandardCharsets.UTF_8)

            // Inject the needed code
            val modified = injectJavaCode(content)

            // Write it back
            val relativePath = file.relativeTo(sourceDir)
            val targetFile = File(outputDir, relativePath.path)
            targetFile.parentFile.mkdirs()
            targetFile.writeText(modified, StandardCharsets.UTF_8)
        }
    }

    /**
     * Inject the needed methods, fields and constructors into wrapped classes
     *
     * @param content The Raw Source code that needs to be processed
     */
    private fun injectJavaCode(content: String): String {
        val cu: CompilationUnit = JavaParser().parse(content).result.getOrNull() ?: return content

        // Try to determine the class, from the source code
        val clazz = cu.types
            .filterIsInstance<ClassOrInterfaceDeclaration>()
            .firstOrNull() ?: return content

        // Process the annotation
        val wrapAnnotation = clazz.annotations
            .filterIsInstance<SingleMemberAnnotationExpr>()
            .firstOrNull { it.nameAsString == "WrapClass" } ?: return content

        // Get a reference to the class that code is wrapping
        val classExpr = wrapAnnotation.memberValue as? ClassExpr ?: return content
        val wrappedType = (classExpr.type as ClassOrInterfaceType).nameAsString

        // Check if the _internal field already exists in the code. If it doesn't, generate it
        if (!clazz.fields.any { it.variables.any { v -> v.nameAsString == "_internal" }}) {
            val field = FieldDeclaration(
                NodeList(Modifier.privateModifier(), Modifier.finalModifier()),
                VariableDeclarator(ClassOrInterfaceType(null, wrappedType), "_internal"),
            )

            clazz.addMember(field)
        }

        // Generate a private constructor that takes in the _internal class a parameter
        val constructor = ConstructorDeclaration()
            .setName(clazz.nameAsString)
            .addModifier(Modifier.Keyword.PRIVATE)
            .addParameter(wrappedType, "internal")
            .setBody(
                BlockStmt().addStatement("this._internal = internal;")
            )

        // Register the constructor if it doesn't already exist
        if (clazz.constructors.none { it.parameters.size == 1 && it.parameters[0].typeAsString == wrappedType }) {
            clazz.addMember(constructor)
        }

        // Generate a static method, to create a new Wrapped instance of the class
        val wrapMethod = MethodDeclaration()
            .addModifier(Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
            .setName("wrap")
            .setType(clazz.nameAsString)
            .addParameter(wrappedType, "internal")
            .setBody(
                BlockStmt().addStatement("return new ${clazz.nameAsString}(internal);")
            )

        if (!clazz.methods.any { it.nameAsString == "wrap"}) {
            clazz.addMember(wrapMethod)
        }

        // Generate the method to unwrap the class, into the original class that it's wrapping
        val unwrapMethod = MethodDeclaration()
            .setPublic(true)
            .setName("unwrap")
            .setType(wrappedType)
            .setBody(
                BlockStmt().addStatement("return this._internal;")
            )

        if (!clazz.methods.any { it.nameAsString == "unwrap" }) {
            clazz.addMember(unwrapMethod)
        }

        return cu.toString()
    }

}