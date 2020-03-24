package com.neo4j.gradle.asciidoctor

import org.asciidoctor.Asciidoctor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File


open class AsciidoctorModuleDescriptorExtension

open class AsciidoctorModuleDescriptorPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.extensions.create("asciidoctorModuleDescriptor", AsciidoctorModuleDescriptorExtension::class.java)
  }
}

abstract class AsciidoctorModuleDescriptorGenerateTask : DefaultTask() {

  private val TESTING_SLUG_PREFIX = "_testing_"

  @InputFiles
  var sources: MutableList<ConfigurableFileTree> = mutableListOf()

  @Input
  var outputDir: String = ""

  @Input
  var moduleName: String = ""

  private val dumperOptions: DumperOptions
    get() {
      val options = DumperOptions()
      options.indent = 2
      options.isPrettyFlow = true
      options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
      options.defaultScalarStyle = DumperOptions.ScalarStyle.DOUBLE_QUOTED
      return options
    }

  private val yaml = Yaml(dumperOptions)

  @TaskAction
  fun task() {
    if (moduleName.isBlank()) {
      throw GradleException("moduleName is mandatory to build the module descriptor, aborting...")
    }
    val asciidoctor = Asciidoctor.Factory.create()
    val pages = sources.asSequence().map { it.sorted() }.flatten().sorted().mapNotNull { file ->
      val document = asciidoctor.loadFile(file, emptyMap())
      val url = "${file.name.removeSuffix(".adoc")}.html"
      val title = document.doctitle
      val slug = document.getAttribute("slug", "") as String
      val hasQuiz = document.findBy(mapOf("context" to ":section", "role" to "quiz")).isNotEmpty()
      val hasCertificate = document.findBy(mapOf("context" to ":section", "role" to "certificate")).isNotEmpty()
      if (slug.isNotBlank()) {
        mapOf(
          "title" to title,
          "url" to url,
          "slug" to slug,
          "quiz" to hasQuiz.toString(),
          "certificate" to hasCertificate.toString()
        )
      } else {
        null
      }
    }.toList()
    val pagesInfo = pages.mapIndexed { index: Int, item: Map<String, String> ->
      if (index + 1 < pages.size) {
        val nextItem = pages[index + 1]
        val nextConfig = mapOf<String, Any>(
          "next" to mapOf(
            "slug" to nextItem["slug"],
            "title" to nextItem["title"]
          )
        )
        item.plus(nextConfig)
      } else {
        item
      }
    }
    val outputDirFile = File(outputDir)
    if (!outputDirFile.exists()) {
      outputDirFile.mkdirs()
    }
    val outputFile = File(outputDir, "asciidoctor-module-descriptor.yml")
    outputFile.writeText(yaml.dump(mapOf(
      "module_name" to moduleName,
      "pages" to pagesInfo
    )))
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
