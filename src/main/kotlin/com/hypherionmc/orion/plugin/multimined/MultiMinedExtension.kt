package com.hypherionmc.orion.plugin.multimined

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hypherionmc.orion.Constants
import com.hypherionmc.orion.plugin.OrionExtension
import com.hypherionmc.orion.utils.Environment
import com.hypherionmc.orion.utils.GradleUtils
import com.hypherionmc.orion.utils.unimined.PaperMCTransformer
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.util.internal.VersionNumber
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.FabricLikeMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.fabric.FabricLikeMinecraftTransformer.Companion.GSON
import xyz.wagyourtail.unimined.util.*
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*
import javax.inject.Inject
import kotlin.io.path.*

open class MultiMinedExtension(private val project: Project) {

    val setup: SetupBlock = project.objects.newInstance(SetupBlock::class.java, project, this)
    private val includeInJarConfig: Configuration? = project.configurations.maybeCreate("includeInJar")

    fun setup(configure: SetupBlock.() -> Unit) {
        setup.configure()
        setup.applySetup()
    }

    // Dependency Helpers
    fun main(dep: Any) = addIfExists("modImplementation", dep)
    fun fabric(dep: Any) = addIfExists("fabricModImplementation", dep)
    fun neoforge(dep: Any) = addIfExists("neoforgeModImplementation", dep)
    fun forge(dep: Any) = addIfExists("forgeModImplementation", dep)
    fun paper(dep: Any) = addIfExists("paperModImplementation", dep)
    fun fabricApi() = addIfExists("fabricModImplementation", "net.fabricmc.fabric-api:fabric-api:${GradleUtils.getProperty(project, "fabric_api")}")
    fun commonInclude(dep: Any) = run {
        project.dependencies.add(includeInJarConfig?.name, dep)
        addIfExists("implementation", dep)
        addIfExists("fabricCompileOnly", dep)
        addIfExists("neoforgeCompileOnly", dep)
        addIfExists("forgeCompileOnly", dep)
        addIfExists("apiImplementation", dep)
    }

    fun api(dep: Any) = addIfExists("apiImplementation", dep)

    fun shade(dep: Any) = shadowConfiguration(dep)

    fun platformEnabled(platform: String): Boolean {
        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
        return sourceSets.findByName(platform) != null
    }

    private fun addIfExists(configName: String, dep: Any) {
        val config = project.configurations.findByName(configName)

        if (config != null) {
            project.dependencies.add(configName, dep)
        }
    }

    private fun shadowConfiguration(dep: Any) {
        val config = project.configurations.findByName("shade")

        if (config != null) {
            project.dependencies.add("shade", dep)
        } else {
            project.configurations.create("shade")
            project.dependencies.add("shade", dep)
        }
    }

    open class SetupBlock @Inject constructor(private val project: Project, private val extension: MultiMinedExtension) {
        var multiLoader: Boolean = false
        val publishMaven: Boolean = false
        private var mcVersion: String? = null
        private var shadowJar: ShadowJarConfig? = null
        private var jarJarExclude: List<String> = listOf<String>().toMutableList()

        private var fabric: LoaderConfiguration = LoaderConfiguration("fabric")
        private var neoforge: LoaderConfiguration = LoaderConfiguration("neoforge")
        private var forge: LoaderConfiguration = LoaderConfiguration("forge")
        private var paper: LoaderConfiguration = LoaderConfiguration("paper")
        private var api: SourceSetConfig? = null

        fun version(v: String) {
            mcVersion = v
        }

        fun fabric(loader: String) {
            fabric = LoaderConfiguration("fabric")
            fabric.version(loader)
        }

        fun fabric(action: Action<LoaderConfiguration>) {
            action.execute(fabric)
        }

        fun neoforge(loader: String, vararg mixins: String) {
            neoforge = LoaderConfiguration("neoforge")
            neoforge.version(loader)
            neoforge.mixinConfig(*mixins)
        }

        fun neoforge(action: Action<LoaderConfiguration>) {
            action.execute(neoforge)
        }

        fun forge(loader: String, vararg mixins: String) {
            forge = LoaderConfiguration("forge")
            forge.version(loader)
            forge.mixinConfig(*mixins)
        }

        fun forge(action: Action<LoaderConfiguration>) {
            action.execute(forge)
        }

        fun paper(loader: String) {
            paper = LoaderConfiguration("paper")
        }

        fun paper(action: Action<LoaderConfiguration>) {
            action.execute(paper)
        }

        fun shadowJar(action: Action<ShadowJarConfig>) {
            shadowJar = ShadowJarConfig()
            action.execute(shadowJar!!)
        }

        fun api(action: Action<SourceSetConfig>) {
            api = SourceSetConfig()
            action.execute(api!!)
        }

        fun getShadowJar(): ShadowJarConfig? {
            return shadowJar
        }

        fun jarJarExclude(exclude: List<String>) {
            jarJarExclude = exclude
        }

        fun applySetup() {
            if (multiLoader) {
                setupSourceSets()
                setupMavenPublishing()
            }
        }

        private fun getOrCreateShadowConfig(): Configuration {
            return project.configurations.findByName("shade") ?: project.configurations.create("shade")
        }

        private fun setupMavenPublishing() {
            project.afterEvaluate {
                val publishing = project.extensions.findByType(PublishingExtension::class.java) ?: return@afterEvaluate
                val orion = project.extensions.findByType(OrionExtension::class.java) ?: return@afterEvaluate
                val publications = publishing.publications

                shadowJar?.let {
                    publications.create("mavenCommon", MavenPublication::class.java) { publication ->
                        publication.artifactId = "${project.name}-Common-${mcVersion}"
                        publication.artifact(project.tasks.named("mainShadowJar")) {
                            it.builtBy(project.tasks.named("mainShadowJar"))
                        }
                    }
                }

                fabric.getVersion()?.let {
                    publications.create("mavenFabric", MavenPublication::class.java) { publication ->
                        publication.artifactId = "${project.name}-Fabric-${mcVersion}"
                        publication.artifact(project.tasks.named("remapFabricJar")) {
                            it.builtBy(project.tasks.named("remapFabricJar"))
                        }
                    }
                }

                neoforge.getVersion()?.let {
                    publications.create("mavenNeoForge", MavenPublication::class.java) { publication ->
                        publication.artifactId = "${project.name}-Neoforge-${mcVersion}"
                        publication.artifact(project.tasks.named("remapNeoforgeJar")) {
                            it.builtBy(project.tasks.named("remapNeoforgeJar"))
                        }
                    }
                }

                forge.getVersion()?.let {
                    publications.create("mavenForge", MavenPublication::class.java) { publication ->
                        publication.artifactId = "${project.name}-Forge-${mcVersion}"
                        publication.artifact(project.tasks.named("remapForgeJar")) {
                            it.builtBy(project.tasks.named("remapForgeJar"))
                        }
                    }
                }

                paper.getVersion()?.let {
                    publications.create("mavenPaper", MavenPublication::class.java) { publication ->
                        publication.artifactId = "${project.name}-Paper-${mcVersion}"
                        publication.artifact(project.tasks.named("remapPaperJar")) {
                            it.builtBy(project.tasks.named("remapPaperJar"))
                        }
                    }
                }

                if (orion.publishApiJar.get()) {
                    api?.let {
                        publications.create("mavenApi", MavenPublication::class.java) { publication ->
                            publication.artifactId = "${project.name}-API"
                            publication.version = project.version.toString()
                            publication.artifact(project.tasks.named("apiShadowJar")) {
                                it.builtBy(project.tasks.named("apiShadowJar"))
                            }
                        }
                    }
                }

                publishing.repositories.maven { repo ->
                    repo.url = URI.create(if (!orion.versioning.identifier.equals("release", ignoreCase = true)) Constants.MAVEN_SNAPSHOT_URL else Constants.MAVEN_URL)
                    repo.credentials { c: PasswordCredentials ->
                        c.username = Environment.getenv("MAVEN_USER")
                        c.password = Environment.getenv("MAVEN_PASS")
                    }
                }
            }
        }

        private fun setupSourceSets() {
            val unimined = project.extensions.findByType(UniminedExtension::class.java)
                ?: error("Unimined plugin must be applied before MultiMined!")

            project.logger.lifecycle("Setting up MultiLoader source sets...")

            val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
            val sourcesList = mutableListOf<String>()

            if (fabric.getVersion() != null) sourcesList.add(fabric.name)
            if (neoforge.getVersion() != null) sourcesList.add(neoforge.name)
            if (paper.getVersion() != null) sourcesList.add(paper.name)
            if (forge.getVersion() != null) sourcesList.add(forge.name)
            if (api != null) sourcesList.add("api")

            sourcesList.forEach {
                if (sourceSets.findByName(it) == null) {
                    sourceSets.create(it)
                }
            }

            if (api != null) {
                val apiSet = sourceSets.getByName("api")

                fun exposeApiTo(sourceSetName: String) {
                    val ss = sourceSets.getByName(sourceSetName)
                    ss.compileClasspath += apiSet.output
                    ss.runtimeClasspath += apiSet.output
                }

                sourceSets.map { it.name }.filter { it != "api" }.forEach(::exposeApiTo)
                project.configurations.named(apiSet.compileOnlyConfigurationName).get().extendsFrom(project.configurations.getByName("compileOnly"))
                project.configurations.named(apiSet.annotationProcessorConfigurationName).get().extendsFrom(project.configurations.getByName("annotationProcessor"))
                project.configurations.named(apiSet.implementationConfigurationName).get().extendsFrom(project.configurations.getByName("implementation"))

                project.tasks.register("apiShadowJar", ShadowJar::class.java) {
                    it.group = "build"
                    it.archiveClassifier.set("")
                    it.archiveBaseName.set("${project.name}-API")
                    it.from(sourceSets.getByName("api").output)

                    val deps = mutableListOf(
                        project.configurations.getByName("shade")
                    )

                    project.configurations.getByName("includeInJar").let { d ->
                         deps.add(d)
                    }

                    it.configurations.set(deps)
                    it.addMultiReleaseAttribute.set(false)

                    val mavenRegex = Regex("""^[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+(\*|\.\*)?$""")

                    if (api!!.getShadowJar()!!.getExclude().isNotEmpty() || api!!.getShadowJar()!!.getRelocate().isNotEmpty()) {
                        // Configure dependencies to exclude
                        it.dependencies { excl ->
                            if (api!!.getShadowJar()!!.getExclude().isNotEmpty()) {
                                api!!.getShadowJar()!!
                                    .getExclude()
                                    .filter { p -> mavenRegex.matches(p) }
                                    .forEach { p -> excl.exclude(excl.dependency(p)) }
                            }
                        }

                        if (api!!.getShadowJar()!!.getExclude().isNotEmpty()) {
                            api!!.getShadowJar()!!
                                .getExclude()
                                .filter { p -> !mavenRegex.matches(p) }
                                .forEach { p -> it.exclude(p) }
                        }

                        // Configure dependencies to relocate
                        if (api!!.getShadowJar()!!.getRelocate().isNotEmpty()) {
                            api!!.getShadowJar()!!.getRelocate().forEach { (from, to) -> it.relocate(from, to) }
                        }
                    }

                    // Configure Service File Merging
                    if (api!!.getShadowJar()!!.getMergeServiceFiles()) {
                        it.mergeServiceFiles()
                    }

                    // Minimize the output file
                    if (api!!.getShadowJar()!!.getMinimize()) {
                        it.minimize()
                    }

                    val attr = mapOf(
                        "Specification-Title" to project.name,
                        "Specification-Version" to project.version,
                        "Implementation-Title" to "Api",
                        "Implementation-Version" to project.version.toString(),
                        "Implementation-Timestamp" to Date().toString(),
                        "Built-On-Java" to "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})",
                    )

                    it.manifest { man ->
                        man.attributes(attr)
                    }
                }

                //project.tasks.getByName("compileApiJava").finalizedBy(project.tasks.getByName("apiShadowJar"))
            }

            val main = sourceSets.getByName("main")

            unimined.minecraft(main, lateApply = false) {
                version(mcVersion ?: error("No Minecraft version specified!"))
                mappings {
                    if (minecraft.obfuscated) {
                        mojmap()
                        devNamespace("mojmap")
                    }
                }

                if (multiLoader && sourceSet.name == "main") {
                    fabric {
                        loader(fabric.getVersion() ?: error("No Fabric Loader version specified!"))
                    }

                    mods.modImplementation {
                        catchAWNamespaceAssertion()
                    }

                    defaultRemapJar = false
                    project.configurations.getByName("compileOnly").extendsFrom(getOrCreateShadowConfig())
                }
            }

            // ShadowJar for common, if needed
            shadowJar?.let { shadow ->
                project.afterEvaluate { p ->
                    val shadowTask = project.tasks.register("mainShadowJar", ShadowJar::class.java) {
                        it.configurations.set(listOf(p.configurations.getByName("shade")))
                        it.archiveClassifier.set("")
                        it.from(main.output)
                        if (api != null) it.from(sourceSets.getByName("api").output)
                        it.archiveBaseName.set("${project.name}-Common-${mcVersion}")
                        it.addMultiReleaseAttribute.set(false)

                        val mavenRegex = Regex("""^[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+(\*|\.\*)?$""")

                        if (shadow.getExclude().isNotEmpty() || shadow.getRelocate().isNotEmpty()) {
                            // Configure dependencies to exclude
                            it.dependencies { excl ->
                                if (shadow.getExclude().isNotEmpty()) {
                                    shadow
                                        .getExclude()
                                        .filter { p -> mavenRegex.matches(p) }
                                        .forEach { p -> excl.exclude(excl.dependency(p)) }
                                }
                            }

                            if (shadow.getExclude().isNotEmpty()) {
                                shadow
                                    .getExclude()
                                    .filter { p -> !mavenRegex.matches(p) }
                                    .forEach { p -> it.exclude(p) }
                            }

                            // Configure dependencies to relocate
                            if (shadow.getRelocate().isNotEmpty()) {
                                shadow.getRelocate().forEach { (from, to) -> it.relocate(from, to) }
                            }
                        }

                        // Configure Service File Merging
                        if (shadow.getMergeServiceFiles()) {
                            it.mergeServiceFiles()
                        }

                        // Minimize the output file
                        if (shadow.getMinimize()) {
                            it.minimize()
                        }

                        val attr = mapOf(
                            "Specification-Title" to project.name,
                            "Specification-Version" to project.version,
                            "Implementation-Title" to "Main",
                            "Implementation-Version" to project.version.toString(),
                            "Implementation-Timestamp" to Date().toString(),
                            "Built-On-Java" to "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})",
                            "Built-On-Minecraft" to mcVersion
                        )

                        it.manifest { man ->
                            man.attributes(attr)
                        }

                        it.manifest.attributes.remove("Multi-Release")
                    }

                    // Make the shadowJar the default output for the main jar task
                    project.tasks.withType(Jar::class.java).named("jar") {
                        it.archiveBaseName.set("${project.name}-Common-${mcVersion}")
                        it.finalizedBy(shadowTask)
                        it.manifest.attributes.remove("Multi-Release")
                    }
                }
            }

            fabric.getVersion()?.let { v ->
                val fb = sourceSets.getByName("fabric")

                unimined.minecraft(fb, lateApply = false) {
                    combineWith(main)

                    fabric {
                        loader(v)
                    }

                    mods.modImplementation {
                        catchAWNamespaceAssertion()

                        if (version < "26.1") {
                            namespace("intermediary")
                        }
                    }
                }

                project.afterEvaluate { p ->
                    val shade = getOrCreateShadowConfig()
                    project.configurations.getByName("fabricCompileOnly").extendsFrom(p.configurations.getByName("compileOnly"), shade)
                    project.configurations.getByName("fabricAnnotationProcessor").extendsFrom(project.configurations.getByName("annotationProcessor"))
                    setupTasks("fabric", fabric, sourceSets)
                }
            }

            api?.let { v ->
                val apis = sourceSets.getByName("api")

                project.afterEvaluate { p ->
                    getOrCreateShadowConfig()
                    project.configurations.named(apis.compileOnlyConfigurationName).get().extendsFrom(project.configurations.getByName("compileOnly"))
                    project.configurations.named(apis.annotationProcessorConfigurationName).get().extendsFrom(project.configurations.getByName("annotationProcessor"))
                }
            }

            neoforge.getVersion()?.let { version ->
                val neo = sourceSets.getByName("neoforge")

                unimined.minecraft(neo, lateApply = false) {
                    combineWith(main)
                    neoForge {
                        loader(version)
                        if (neoforge.getMixinConfig().isNotEmpty()) {
                            mixinConfig(neoforge.getMixinConfig())
                        }
                    }

                    mods.modImplementation {
                        catchAWNamespaceAssertion()
                    }
                }

                project.afterEvaluate { p ->
                    val shade = getOrCreateShadowConfig()
                    project.configurations.getByName("neoforgeCompileOnly").extendsFrom(p.configurations.getByName("compileOnly"), shade)
                    project.configurations.getByName("neoforgeAnnotationProcessor").extendsFrom(project.configurations.getByName("annotationProcessor"))
                    setupTasks("neoforge", neoforge, sourceSets)
                }
            }

            forge.getVersion()?.let { version ->
                val fg = sourceSets.getByName("forge")

                unimined.minecraft(fg, lateApply = false) {
                    combineWith(main)

                    minecraftForge {
                        loader(version)

                        if (forge.getMixinConfig().isNotEmpty()) {
                            mixinConfig(forge.getMixinConfig())
                        }
                    }

                    mods.modImplementation {
                        catchAWNamespaceAssertion()
                    }
                }

                project.afterEvaluate { p ->
                    val shade = getOrCreateShadowConfig()
                    project.configurations.getByName("forgeCompileOnly").extendsFrom(p.configurations.getByName("compileOnly"), shade)
                    project.configurations.getByName("forgeAnnotationProcessor").extendsFrom(project.configurations.getByName("annotationProcessor"))
                    setupTasks("forge", forge, sourceSets)
                }
            }

            paper.getVersion()?.let { version ->
                val ppr = sourceSets.getByName("paper")

                unimined.minecraft(ppr, lateApply = false) {
                    combineWith(main)
                    side("server")

                    customPatcher(PaperMCTransformer(project, this as MinecraftProvider)) {
                        loader(version)
                    }
                }

                project.afterEvaluate { p ->
                    val shade = getOrCreateShadowConfig()
                    project.dependencies.add("paperCompileOnly","io.papermc.paper:paper-api:${mcVersion}-R0.1-SNAPSHOT")
                    project.configurations.getByName("paperCompileOnly").extendsFrom(p.configurations.getByName("compileOnly"), shade)
                    project.configurations.getByName("paperAnnotationProcessor").extendsFrom(project.configurations.getByName("annotationProcessor"))
                    setupTasks("paper", paper, sourceSets, true)
                }
            }

        }

        private fun setupTasks(sourceSet: String, loader: LoaderConfiguration, sourceSets: SourceSetContainer, isPaperJar: Boolean = false) {
            if (loader.getShadowJar() != null) {
                // ShadowJar task
                val shadowTask = project.tasks.register("${sourceSet}ShadowJar", ShadowJar::class.java) {
                    it.configurations.set(listOf(project.configurations.getByName("shade")))
                    it.archiveClassifier.set("${sourceSet}-dev-shadow")
                    it.addMultiReleaseAttribute.set(false)

                    // Use Compiled Output as inputs for ShadowJar
                    if (!isPaperJar) it.from(sourceSets.getByName("main").output)
                    if (api != null) it.from(sourceSets.getByName("api").output)
                    it.from(sourceSets.getByName(sourceSet).output)

                    val mavenRegex = Regex("""^[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+(\*|\.\*)?$""")

                    if (loader.getShadowJar()!!.getExclude().isNotEmpty() || loader.getShadowJar()!!.getRelocate().isNotEmpty()) {
                        // Configure dependencies to exclude
                        it.dependencies { excl ->
                            if (loader.getShadowJar()!!.getExclude().isNotEmpty()) {
                                loader.getShadowJar()!!
                                    .getExclude()
                                    .filter { p -> mavenRegex.matches(p) }
                                    .forEach { p -> excl.exclude(excl.dependency(p)) }
                            }
                        }

                        if (loader.getShadowJar()!!.getExclude().isNotEmpty()) {
                            loader.getShadowJar()!!
                                .getExclude()
                                .filter { p -> !mavenRegex.matches(p) }
                                .forEach { p -> it.exclude(p) }
                        }

                        // Configure dependencies to relocate
                        if (loader.getShadowJar()!!.getRelocate().isNotEmpty()) {
                            loader.getShadowJar()!!.getRelocate().forEach { (from, to) -> it.relocate(from, to) }
                        }
                    }

                    // Configure Service File Merging
                    if (loader.getShadowJar()!!.getMergeServiceFiles()) {
                        it.mergeServiceFiles()
                    }

                    // Minimize the output file
                    if (loader.getShadowJar()!!.getMinimize()) {
                        it.minimize()
                    }

                    var attr = mapOf(
                        "Specification-Title" to project.name,
                        "Specification-Version" to project.version,
                        "Implementation-Title" to StringUtils.capitalize(sourceSet),
                        "Implementation-Version" to project.version.toString(),
                        "Implementation-Timestamp" to Date().toString(),
                        "Built-On-Java" to "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})",
                        "Built-On-Minecraft" to mcVersion
                    )

                    if (loader.getMixinConfig().isNotEmpty()) {
                        attr = attr + ("MixinConfigs" to loader.getMixinConfig().joinToString(","))
                    }

                    it.manifest { man ->
                        man.attributes(attr)
                    }

                    it.manifest.attributes.remove("Multi-Release")
                }

                shadowTask.get().doLast {
                    if (loader.name.equals("forge", true) || loader.name.equals("neoforge", true)) {
                        doJarJar(shadowTask.get().archiveFile.get().asFile.toPath())
                    } else  if (loader.name.equals("fabric", true)) {
                        insertIncludes(loader, shadowTask.get().archiveFile.get().asFile.toPath())
                    }
                }

                // Configure RemapJar task to use ShadowJar output
                project.tasks.withType(RemapJarTask::class.java).named("remap${StringUtils.capitalize(sourceSet)}Jar") {
                    it.inputFile.set(shadowTask.get().archiveFile)
                    it.asJar.archiveClassifier.set(null as String?)
                    it.asJar.archiveBaseName.set("${project.name}-${StringUtils.capitalize(sourceSet)}-${mcVersion}")
                    it.asJar.manifest.attributes.remove("Multi-Release")

                    val mcVer = VersionNumber.parse(mcVersion)
                    val notObfedMc = VersionNumber.parse("1.20.5")

                    if (isPaperJar && (mcVer >= notObfedMc)) {
                        it.prodNamespace("mojmap")
                    }

                    it.doLast {
                        project.delete(shadowTask.get().archiveFile)
                    }
                }

                project.tasks.withType(Jar::class.java).named("${sourceSet}Jar") {
                    it.archiveClassifier.set("${sourceSet}-slim")
                    it.manifest.attributes.remove("Multi-Release")
                }
            } else {
                project.tasks.withType(RemapJarTask::class.java).named("remap${StringUtils.capitalize(sourceSet)}Jar") { itt ->
                    itt.doLast {
                        if (loader.name.equals("forge", true) || loader.name.equals("neoforge", true)) {
                            doJarJar(itt.asJar.archiveFile.get().asFile.toPath())
                        } else if (loader.name.equals("fabric", true)) {
                            insertIncludes(loader, itt.asJar.archiveFile.get().asFile.toPath())
                        }
                    }
                }
            }

            // Process Resources
            project.tasks.withType(ProcessResources::class.java).named("process${StringUtils.capitalize(sourceSet)}Resources") {
                val buildProps = project.properties.toMutableMap()
                it.filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml", "paper-plugin.yml", "pack.mcmeta")) { f ->
                    f.expand(buildProps)
                }
            }
        }

        private fun doJarJar(output: Path) {
            val includeConfig = project.configurations.findByName("includeInJar") ?: return
            val deps = includeConfig.incoming.artifacts.resolvedArtifacts.get().filter {
                j -> !jarJarExclude.contains(j.getCoords().group) && !jarJarExclude.contains(j.getCoords().artifact) && !jarJarExclude.contains("${j.getCoords().group}:${j.getCoords().artifact}") }
                .toList()

            if (deps.isEmpty())
                return

            output.openZipFileSystem(mapOf("mutable" to true)).use { fs ->
                val json = JsonObject()
                val jarDir = fs.getPath("META-INF/jarjar/")
                val mod = jarDir.resolve("metadata.json")
                jarDir.createDirectories()

                var errored = false

                for (dep in deps) {
                    val location = dep.getCoords()

                    if (location.version == null) {
                        error("Attempted to nest dependency with unknown version ${dep.variant.owner}")
                    }

                    try {
                        val path = jarDir.resolve(location.fileName)
                        if (!path.exists()) {
                            dep.file.toPath()
                                .copyTo(jarDir.resolve(location.fileName), true)
                        }

                        addIncludeToMetadata(json, location, "META-INF/jarjar/${location.fileName}")
                    } catch (e: Exception) {
                        project.logger.error("Failed on $dep", e)
                        errored = true
                    }
                }
                if (errored) {
                    throw IllegalStateException("An error occured resolving includes")
                }

                mod.writeBytes(FabricLikeMinecraftTransformer.GSON.toJson(json).toByteArray())
            }
        }

        fun getLocalCache(): Path {
            return project.projectDir.toPath().resolve(".gradle").resolve("orion").resolve("local")
                .createDirectories()
        }

        private fun insertIncludes(loader: LoaderConfiguration, output: Path) {
            val includeConfig = project.configurations.findByName("includeInJar") ?: return
            val deps = includeConfig.incoming.artifacts.resolvedArtifacts.get().filter {
                    j -> !jarJarExclude.contains(j.getCoords().group) && !jarJarExclude.contains(j.getCoords().artifact) && !jarJarExclude.contains("${j.getCoords().group}:${j.getCoords().artifact}") }
                .toList()
            if (deps.isEmpty()) {
                return
            }
            output.openZipFileSystem(mapOf("mutable" to true)).use { fs ->
                val includeCache = getLocalCache().resolve("includeCache${loader.name}")
                val jars = fs.getPath("META-INF/jars")

                val mod = fs.getPath("fabric.mod.json")
                if (!Files.exists(mod)) {
                    throw IllegalStateException("fabric.mod.json not found in jar")
                }
                val json = JsonParser.parseReader(InputStreamReader(Files.newInputStream(mod))).asJsonObject

                Files.createDirectories(jars)
                Files.createDirectories(includeCache)
                var errored = false
                for (dep in deps) {
                    val location = dep.getCoords()

                    if (location.version == null) {
                        error("Attempted to nest dependency with unknown version ${dep.variant.owner}")
                    }

                    if (location.extension != "jar") {
                        project.logger.info("Skipping $location because it is not a jar")
                    } else {
                        project.logger.info("Adding $location to jar")
                    }
                    try {
                        val source = dep.file.toPath()
                        val path = jars.resolve(location.fileName)
                        if (!source.zipContains("fabric.mod.json")) {
                            val cachePath = includeCache.resolve("${source.nameWithoutExtension}-${source.getShortSha1()}.${source.extension}")
                            if (!cachePath.exists() || project.unimined.forceReload || project.gradle.startParameter.isRefreshDependencies) {
                                try {
                                    ZipArchiveOutputStream(
                                        cachePath.outputStream(
                                            StandardOpenOption.CREATE,
                                            StandardOpenOption.TRUNCATE_EXISTING
                                        )
                                    ).use { out ->
                                        source.forEntryInZip { entry, stream ->
                                            out.putArchiveEntry(entry)
                                            stream.copyTo(out)
                                            out.closeArchiveEntry()
                                        }
                                        out.putArchiveEntry(ZipArchiveEntry("fabric.mod.json").also { entry ->
                                            entry.time = CONSTANT_TIME_FOR_ZIP_ENTRIES
                                        })
                                        val innerjson = JsonObject()
                                        innerjson.addProperty("schemaVersion", 1)
                                        var artifactString = ""
                                        if (location.group != null) {
                                            artifactString += location.group + "_"
                                        }
                                        artifactString += location.artifact
                                        if (location.classifier != null) {
                                            artifactString += "_${location.classifier}"
                                        }
                                        if (artifactString.length > 64) {
                                            artifactString = artifactString.substring(0, 50) + artifactString.getSha256(0, 14)
                                        }
                                        innerjson.addProperty("id", artifactString.replace(".", "_").lowercase())
                                        innerjson.addProperty("version",location.version)
                                        innerjson.addProperty("name", location.artifact)
                                        val custom = JsonObject()
                                        custom.addProperty("fabric-loom:generated", true)
                                        custom.addProperty("unimined:generated", true)
                                        innerjson.add("custom", custom)
                                        out.write(GSON.toJson(innerjson).toByteArray())
                                        out.closeArchiveEntry()
                                    }
                                } catch (e: Exception) {
                                    project.logger.error(
                                        "Failed to create fabric.mod.json stub for ${source.absolutePathString()}.",
                                        e
                                    )
                                    throw e
                                }
                            }
                            cachePath.copyTo(path, StandardCopyOption.REPLACE_EXISTING)
                        } else {
                            source.copyTo(path, StandardCopyOption.REPLACE_EXISTING)
                        }

                        addIncludeToModJson(json, path.toString().removePrefix("/"))
                    } catch (e: Exception) {
                        project.logger.error("Failed on $dep", e)
                        errored = true
                    }
                }
                if (errored) {
                    throw IllegalStateException("An error occurred resolving includes")
                }
                Files.write(mod, GSON.toJson(json).toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
            }
        }

        fun addIncludeToModJson(json: JsonObject, path: String) {
            var jars = json.get("jars")?.asJsonArray
            if (jars == null) {
                jars = JsonArray()
                json.add("jars", jars)
            }
            jars.add(JsonObject().apply {
                addProperty("file", path)
            })
        }

        private fun addIncludeToMetadata(json: JsonObject, dep: MavenCoords, path: String) {
            var jars = json.get("jars")?.asJsonArray
            if (jars == null) {
                jars = JsonArray()
                json.add("jars", jars)
            }
            jars.add(JsonObject().apply {
                add("identifier", JsonObject().apply {
                    addProperty("group", dep.group)
                    addProperty("artifact", dep.artifact)
                })
                add("version", JsonObject().apply {
                    addProperty("range", "[${dep.version},)")
                    addProperty("artifactVersion", dep.version)
                })
                addProperty("path", path)
            })
        }
    }

    open class SourceSetConfig {
        private var shadowJar: ShadowJarConfig? = null

        fun shadowJar(action: Action<ShadowJarConfig>) {
            shadowJar = ShadowJarConfig()
            action.execute(shadowJar!!)
        }

        fun getShadowJar(): ShadowJarConfig? {
            return shadowJar
        }
    }

    open class LoaderConfiguration(var name: String): SourceSetConfig() {
        private var version: String? = null
        private var mixinConfig: MutableList<String> = emptyList<String>().toMutableList()

        fun mixinConfig(vararg configs: String) {
            mixinConfig.addAll(configs.toList())
        }

        fun version(v: String) {
            version = v
        }

        fun getMixinConfig(): List<String> {
            return mixinConfig
        }

        fun getVersion(): String? {
            return version
        }
    }

    open class ShadowJarConfig {
        private var exclude: MutableList<String> = emptyList<String>().toMutableList()
        private var minimize: Boolean = false
        private var mergeServiceFiles: Boolean = false
        private var relocate: MutableList<Pair<String, String>> = emptyList<Pair<String, String>>().toMutableList()

        fun exclude(vararg packages: String) {
            exclude.addAll(packages.toList())
        }

        fun minimize() {
            minimize = true
        }

        fun mergeServiceFiles() {
            mergeServiceFiles = true
        }

        fun relocate(vararg relocations: Pair<String, String>) {
            relocate.addAll(relocations.toList())
        }

        fun getExclude(): List<String> {
            return exclude
        }

        fun getMinimize(): Boolean {
            return minimize
        }

        fun getMergeServiceFiles(): Boolean {
            return mergeServiceFiles
        }

        fun getRelocate(): List<Pair<String, String>> {
            return relocate
        }
    }

}