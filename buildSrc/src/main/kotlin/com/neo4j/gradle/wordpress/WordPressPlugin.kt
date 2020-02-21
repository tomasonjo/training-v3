package com.neo4j.gradle.wordpress

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.yaml.snakeyaml.Yaml
import java.io.FileInputStream
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


open class WordPressExtension(objects: ObjectFactory) {
  val scheme: Property<String> = objects.property()
  val host: Property<String> = objects.property()
  val username: Property<String> = objects.property()
  val password: Property<String> = objects.property()
}

open class WordPressPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.extensions.create("wordpress", WordPressExtension::class.java)
  }
}

abstract class WordPressUploadTask : DefaultTask() {

  @InputFiles
  var sources: MutableList<ConfigurableFileTree> = mutableListOf()

  @Input
  var postType: String = ""

  @Input
  // publish, future, draft, pending, private
  var postStatus: String = "draft"

  @Input
  var scheme: String  = "https"

  @Input
  var host: String = ""

  @Input
  var username: String = ""

  @Input
  var password: String = ""

  @TaskAction
  fun task() {
    if (postType.isBlank()) {
      logger.error("postType is mandatory, aborting...")
      return
    }
    val wordPressExtension = project.extensions.findByType(WordPressExtension::class.java)
    val hostValue = wordPressExtension?.host?.getOrElse(host) ?: host
    val schemeValue = wordPressExtension?.scheme?.getOrElse(scheme) ?: scheme
    val httpClient = httpClient()
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    val postsUrl = HttpUrl.Builder()
            .scheme(schemeValue)
            .host(hostValue)
            .addPathSegment("wp")
            .addPathSegment("v2")
            .addPathSegment("posts")
            .build()

    val yaml = Yaml()
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    formatter.format(Date())
    val date = formatter.format(Date())
    sources.forEach { source ->
      source.forEach forEachFile@{ file ->
        val yamlFile = Paths.get(file.toPath().parent.toString(), "${file.nameWithoutExtension}.yml").toFile()
        val fileName = file.name
        val yamlFileAbsolutePath = yamlFile.absolutePath
        if (!yamlFile.exists()) {
          logger.warn("Missing YAML file: $yamlFileAbsolutePath, unable to publish $fileName to WordPress")
          return@forEachFile
        }
        logger.quiet("Loading $yamlFile")
        val data = yaml.load(FileInputStream(yamlFile)) as Map<*, *>
        logger.quiet("Data: $data")
        val slug = getSlug(data, yamlFileAbsolutePath, fileName) ?: return@forEachFile
        val title = getTitle(data, yamlFileAbsolutePath, fileName) ?: return@forEachFile
        val contentHtml = file.readText(Charsets.UTF_8)
        // The terms assigned to the object in the post_tag taxonomy.
        val tags = getTags(data)
        /*
        // The ID for the author of the object.
        val author = 0
        // The excerpt for the object.
        val excerpt = ""
        // The ID of the featured media for the object
        val featured_media = 0
        // Whether or not comments are open on the object.
        // One of: open, closed
        val comment_status = ""
        // Whether or not the object can be pinged.
        // One of: open, closed
        val ping_status = ""
        // The format for the object.
        // One of: standard, aside, chat, gallery, link, image, quote, status, video, audio
        val format = ""
        // Whether or not the object should be treated as sticky
        val sticky = false
        // The theme file to use to display the object
        val template = ""
        // Meta fields.
        // An array of key and value.
        val meta = arrayOf<Map<String, String>>()
        // The terms assigned to the object in the category taxonomy.
        val categories = arrayOf<String>()
         */
        // FIXME: check if the post already exists using https://developer.wordpress.org/rest-api/reference/posts/#list-posts
        // ?slug=$slug
        // if the post exists, update otherwise create!
        // update is the same as create but we should add the page id: POST /wp/v2/posts/<id>
        val body = """{
          |"date_gmt": "$date",
          |"slug": "$slug",
          |"status": "$postStatus",
          |"status": "$postStatus",
          |"title": "$title",
          |"content": {
          |  "rendered": "$contentHtml"
          |},
          |"tags": [${tags.map { "\"$it\"" }.joinToString { ", " }}],
          |"template": "$postType"
          |}""".trimMargin().toRequestBody(jsonMediaType)
        val request = Request.Builder()
                .url(postsUrl)
                .post(body)
                .build()
        httpClient.newCall(request).execute().use {
          if (it.isSuccessful) {
            logger.quiet("Request is successful!")
          }
        }
      }
    }
  }

  private fun getMandatoryString(data: Map<*, *>, name: String, yamlFilePath: String, fileName: String): String? {
    val value = data[name]
    if (value == null) {
      logger.warn("No $name found in: $yamlFilePath, unable to publish $fileName to WordPress")
      return null
    }
    if (value !is String) {
      logger.warn("$name must be a String in: $yamlFilePath, unable to publish $fileName to WordPress")
      return null
    }
    if (value.isBlank()) {
      logger.warn("$name must not be blank in: $yamlFilePath, unable to publish $fileName to WordPress")
      return null
    }
    return value
  }

  private fun getTags(data: Map<*, *>): List<String> {
    val value = data["tags"] ?: return listOf()
    if (value is List<*>) {
      return value.filterIsInstance<String>()
    }
    return listOf()
  }

  private fun getTitle(data: Map<*, *>, yamlFilePath: String, fileName: String): String? {
    return getMandatoryString(data, "title", yamlFilePath, fileName)
  }

  private fun getSlug(data: Map<*, *>, yamlFilePath: String, fileName: String): String? {
    return getMandatoryString(data, "slug", yamlFilePath, fileName)
  }

  private fun httpClient(): OkHttpClient {
    val wordPressExtension = project.extensions.findByType(WordPressExtension::class.java)
    val usernameValue = wordPressExtension?.username?.getOrElse(username) ?: username
    val passwordValue = wordPressExtension?.password?.getOrElse(password) ?: password
    val client = OkHttpClient.Builder()
            .authenticator(object : Authenticator {
              override fun authenticate(route: Route?, response: Response): Request? {
                val credential = Credentials.basic(usernameValue, passwordValue)
                return response.request.newBuilder().header("Authorization", credential).build()
              }
            })
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
    return client.build()
  }

  fun setSource(sources: FileCollection) {
    sources.forEach {
      this.sources.add(project.fileTree(it))
    }
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
