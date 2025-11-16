package com.hypherionmc.orion.plugin.multimined

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.hypherionmc.orion.utils.GradleUtils
import com.hypherionmc.orion.utils.unimined.PaperMCTransformer
import org.apache.commons.lang3.StringUtils
import org.apache.maven.artifact.versioning.ArtifactVersion
import org.apache.maven.artifact.versioning.VersionRange
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.util.internal.VersionNumber
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.minecraft.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import java.util.Date
import javax.inject.Inject

open class MultiMinedExtension(private val project: Project) {

    val setup: SetupBlock = project.objects.newInstance(SetupBlock::class.java, project)

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

    open class SetupBlock @Inject constructor(private val project: Project) {
        var multiLoader: Boolean = false
        private var mcVersion: String? = null

        private var fabric: LoaderConfiguration = LoaderConfiguration("fabric")
        private var neoforge: LoaderConfiguration = LoaderConfiguration("neoforge")
        private var forge: LoaderConfiguration = LoaderConfiguration("forge")
        private var paper: LoaderConfiguration = LoaderConfiguration("paper")

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

        fun applySetup() {
            if (multiLoader) {
                setupSourceSets()
            }
        }

        private fun getOrCreateShadowConfig(): Configuration {
            return project.configurations.findByName("shade") ?: project.configurations.create("shade")
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

            sourcesList.forEach {
                if (sourceSets.findByName(it) == null) {
                    sourceSets.create(it)
                }
            }

            val main = sourceSets.getByName("main")

            unimined.minecraft(main, lateApply = false) {
                version(mcVersion ?: error("No Minecraft version specified!"))
                mappings {
                    mojmap()
                    devNamespace("mojmap")
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

            fabric.getVersion()?.let { version ->
                val fb = sourceSets.getByName("fabric")

                unimined.minecraft(fb, lateApply = false) {
                    combineWith(main)

                    fabric {
                        loader(version)
                    }

                    mods.modImplementation {
                        catchAWNamespaceAssertion()
                        namespace("intermediary")
                    }
                }

                project.afterEvaluate { p ->
                    val shade = getOrCreateShadowConfig()
                    project.configurations.getByName("fabricCompileOnly").extendsFrom(p.configurations.getByName("compileOnly"), shade)
                    setupTasks("fabric", fabric, sourceSets)
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

                    // Use Compiled Output as inputs for ShadowJar
                    if (!isPaperJar) it.from(sourceSets.getByName("main").output)
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
                }

                // Configure RemapJar task to use ShadowJar output
                project.tasks.withType(RemapJarTask::class.java).named("remap${StringUtils.capitalize(sourceSet)}Jar") {
                    it.inputFile.set(shadowTask.get().archiveFile)
                    it.asJar.archiveClassifier.set(null as String?)
                    it.asJar.archiveBaseName.set("${project.name}-${StringUtils.capitalize(sourceSet)}-${mcVersion}")

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
                }

                // Process Resources
                project.tasks.withType(ProcessResources::class.java).named("process${StringUtils.capitalize(sourceSet)}Resources") {
                    val buildProps = project.properties.toMutableMap()
                    it.filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml", "paper-plugin.yml", "pack.mcmeta")) { f ->
                        f.expand(buildProps)
                    }
                }
            }
        }
    }

    open class LoaderConfiguration(var name: String) {
        private var version: String? = null
        private var mixinConfig: MutableList<String> = emptyList<String>().toMutableList()
        private var shadowJar: ShadowJarConfig? = null

        fun shadowJar(action: Action<ShadowJarConfig>) {
            shadowJar = ShadowJarConfig()
            action.execute(shadowJar!!)
        }

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

        fun getShadowJar(): ShadowJarConfig? {
            return shadowJar
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

}