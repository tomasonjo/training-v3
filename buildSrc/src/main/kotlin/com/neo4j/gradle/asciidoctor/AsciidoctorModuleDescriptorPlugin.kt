package com.neo4j.gradle.asciidoctor

import org.asciidoctor.Asciidoctor
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File


open class AsciidoctorModuleDescriptorExtension

open class AsciidoctorModuleDescriptorPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.extensions.create("asciidoctorModuleDescriptor", AsciidoctorModuleDescriptorExtension::class.java)
  }
}

abstract class AsciidoctorModuleDescriptorGenerateTask : DefaultTask() {
  @InputFiles
  var sources: MutableList<ConfigurableFileTree> = mutableListOf()

  @Input
  var outputDir: String = ""

  @TaskAction
  fun task() {
    val asciidoctor = Asciidoctor.Factory.create()
    val navItems = sources.map { it.sorted() }.flatten().sorted().map { file ->
      val document = asciidoctor.loadFile(file, emptyMap())
      val url = "${file.name.removeSuffix(".adoc")}.html"
      val title = document.doctitle
      val slug = document.getAttribute("slug", "")
      """
- title: "$title"
  url: "$url"
  slug: "$slug"
  """.trim()
    }.joinToString("\n")
    val content = """
nav:
${navItems.prependIndent("  ")}
""".trimIndent()
    val outputDirFile = File(outputDir)
    if (!outputDirFile.exists()) {
      outputDirFile.mkdirs()
    }
    File(outputDir, "asciidoctor-module-descriptor.yml").writeText(content)
  }

  fun setSource(source: String) {
    this.sources.add(project.fileTree(source))
  }

  fun setSource(vararg sources: String?) {
    sources.forEach {
      if (it != null) {
        this.sources.add(project.fileTree(it))
      }
    }
  }

  fun setSource(sources: List<String>) {
    sources.forEach {
      this.sources.add(project.fileTree(it))
    }
  }

  fun setSource(source: ConfigurableFileTree) {
    this.sources.add(source)
  }
}
