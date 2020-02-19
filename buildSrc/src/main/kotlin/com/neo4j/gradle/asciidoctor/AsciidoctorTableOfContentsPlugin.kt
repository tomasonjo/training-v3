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


open class AsciidoctorTableOfContentsExtension

open class AsciidoctorTableOfContentsPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.extensions.create("asciidoctorTableOfContents", AsciidoctorTableOfContentsExtension::class.java)
  }
}

abstract class AsciidoctorTableOfContentsGenerateTask : DefaultTask() {
  @InputFiles
  var sources: MutableList<ConfigurableFileTree> = mutableListOf()

  @Input
  var outputDir: String = ""

  @TaskAction
  fun task() {
    val asciidoctor = Asciidoctor.Factory.create()
    val data = sources.map { it.sorted() }.flatten().sorted().map { file ->
      val document = asciidoctor.loadFile(file, emptyMap())
      "${file.name.removeSuffix(".adoc")}.html" to document.doctitle
    }.toMap()
    val content = """ASCIIDOCTOR_TABLE_OF_CONTENTS = {
${data.map { "\"${it.key}\" => \"${it.value}\"" }.joinToString(", ")}
}
"""
    File(outputDir, "asciidoctor_table_of_contents.rb").writeText(content)
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
