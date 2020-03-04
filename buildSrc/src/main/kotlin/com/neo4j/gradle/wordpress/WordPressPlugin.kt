package com.neo4j.gradle.wordpress

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
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
import java.time.Duration
import java.util.*


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

data class DocumentAttributes(val slug: String, val title: String, val tags: List<String>, val content: String)

abstract class WordPressUploadTask : DefaultTask() {

  @InputFiles
  var sources: MutableList<ConfigurableFileTree> = mutableListOf()

  @Input
  var type: String = ""

  @Input
  // publish, future, draft, pending, private
  var status: String = "draft"

  @Input
  var scheme: String = "https"

  @Input
  var host: String = ""

  @Input
  var username: String = ""

  @Input
  var password: String = ""

  @Input
  var template: String = ""

  @TaskAction
  fun task() {
    if (type.isBlank()) {
      logger.error("The type is mandatory, aborting...")
      return
    }
    val wordPressUpload = WordPressUpload(
      documentType = WordPressDocumentType(type),
      documentStatus = status,
      documentTemplate = template,
      sources = sources,
      connectionInfo = wordPressConnectionInfo(),
      logger = logger
    )
    wordPressUpload.publish()
  }

  private fun wordPressConnectionInfo(): WordPressConnectionInfo {
    val wordPressExtension = project.extensions.findByType(WordPressExtension::class.java)
    val hostValue = wordPressExtension?.host?.getOrElse(host) ?: host
    val schemeValue = wordPressExtension?.scheme?.getOrElse(scheme) ?: scheme
    val usernameValue = wordPressExtension?.username?.getOrElse(username) ?: username
    val passwordValue = wordPressExtension?.password?.getOrElse(password) ?: password
    return WordPressConnectionInfo(
      scheme = schemeValue,
      host = hostValue,
      username = usernameValue,
      password = passwordValue
    )
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

data class WordPressConnectionInfo(val scheme: String,
                                   val host: String,
                                   val username: String,
                                   val password: String,
                                   val connectTimeout: Duration = Duration.ofSeconds(10),
                                   val writeTimeout: Duration = Duration.ofSeconds(10),
                                   val readTimeout: Duration = Duration.ofSeconds(30))

data class WordPressDocumentType(val name: String) {
  val urlPath: String = when (name) {
    // type is singular but endpoint is plural for built-in types post and page.
    "post" -> "posts"
    "page" -> "pages"
    else -> name
  }
}

data class WordPressDocument(val id: Int,
                             val slug: String,
                             val type: WordPressDocumentType)

internal class WordPressUpload(val documentType: WordPressDocumentType,
                               val documentStatus: String,
                               val documentTemplate: String,
                               val sources: MutableList<ConfigurableFileTree>,
                               val connectionInfo: WordPressConnectionInfo,
                               val logger: Logger) {

  private val yaml = Yaml()
  private val klaxon = Klaxon()
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
  private val httpClient = httpClient()
  private fun baseUrlBuilder() = HttpUrl.Builder()
    .scheme(connectionInfo.scheme)
    .host(connectionInfo.host)
    .addPathSegment("wp-json")
    .addPathSegment("wp")
    .addPathSegment("v2")

  fun publish(): Boolean {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    formatter.format(Date())
    val date = formatter.format(Date())
    val documentsWithAttributes = getDocumentsWithAttributes()
    if (documentsWithAttributes.isEmpty()) {
      logger.info("No file to upload")
      return false
    }
    val slugs = documentsWithAttributes.map { it.slug }
    val searchUrl = baseUrlBuilder()
      .addPathSegment(documentType.urlPath)
      .addQueryParameter("per_page", slugs.size.toString())
      .addQueryParameter("slug", slugs.joinToString(","))
      .addQueryParameter("status", "publish,future,draft,pending,private")
      .build()
    val credential = Credentials.basic(connectionInfo.username, connectionInfo.password)
    val searchRequest = Request.Builder()
      .url(searchUrl)
      // force the header because WordPress returns a 400 instead of a 401 when the authentication fails...
      .header("Authorization", credential)
      .get()
      .build()
    val wordPressDocumentsBySlug = executeRequest(searchRequest) { responseBody ->
      try {
        val jsonArray = klaxon.parseJsonArray(responseBody.charStream())
        jsonArray.value.mapNotNull { item ->
          if (item is JsonObject) {
            val slug = item.string("slug")!!
            slug to WordPressDocument(item.int("id")!!, slug, documentType)
          } else {
            null
          }
        }.toMap()
      } catch (e: KlaxonException) {
        logger.error("Unable to parse the response", e)
        null
      }
    } ?: return false
    for (documentAttributes in documentsWithAttributes) {
      val wordPressDocument = wordPressDocumentsBySlug[documentAttributes.slug]
      val data = mutableMapOf<String, Any>(
        "date_gmt" to date,
        "slug" to documentAttributes.slug,
        "status" to documentStatus,
        "title" to documentAttributes.title,
        "content" to documentAttributes.content,
        // fixme: expect a list of ids "{"tags":"tags[0] is not of type integer."}"
        //"tags" to documentAttributes.tags,
        "type" to documentType
      )
      if (documentTemplate.isNotBlank()) {
        data["template"] = documentTemplate
      }
      if (wordPressDocument != null) {
        // document already exists on WordPress, updating...
        updateDocument(data, wordPressDocument)
      } else {
        // document does not exist on WordPress, creating...
        createDocument(data)
      }
    }
    return true
  }

  private fun updateDocument(data: MutableMap<String, Any>, wordPressDocument: WordPressDocument): Boolean {
    data["id"] = wordPressDocument.id
    val url = baseUrlBuilder()
      .addPathSegment(documentType.urlPath)
      .addPathSegment(wordPressDocument.id.toString())
      .build()
    logger.debug("POST $url")
    val updateRequest = Request.Builder()
      .url(url)
      .post(klaxon.toJsonString(data).toRequestBody(jsonMediaType))
      .build()
    return executeRequest(updateRequest) { responseBody ->
      try {
        val jsonObject = klaxon.parseJsonObject(responseBody.charStream())
        val id = jsonObject.int("id")!!
        logger.quiet("Successfully updated the ${documentType.name.toLowerCase()} with id: $id and slug: ${data["slug"]}")
        true
      } catch (e: KlaxonException) {
        logger.error("Unable to parse the response for the ${documentType.name.toLowerCase()} with slug: ${data["slug"]}", e)
        false
      }
    } ?: false
  }

  private fun createDocument(data: MutableMap<String, Any>): Boolean {
    val url = baseUrlBuilder()
      .addPathSegment(documentType.urlPath)
      .build()
    logger.debug("POST $url")
    val createRequest = Request.Builder()
      .url(url)
      .post(klaxon.toJsonString(data).toRequestBody(jsonMediaType))
      .build()
    return executeRequest(createRequest) { responseBody ->
      try {
        val jsonObject = klaxon.parseJsonObject(responseBody.charStream())
        val id = jsonObject.int("id")!!
        logger.quiet("Successfully created a new ${documentType.name.toLowerCase()} with id: $id and slug: ${data["slug"]}")
        true
      } catch (e: KlaxonException) {
        logger.error("Unable to parse the response for the new ${documentType.name.toLowerCase()} with slug: ${data["slug"]}", e)
        false
      }
    } ?: false
  }

  /**
   * Get a list of documents with attributes (read from a YAML file).
   * The YAML file is generated in a pre-task.
   */
  private fun getDocumentsWithAttributes(): List<DocumentAttributes> {
    return sources
      .flatten()
      .filter { it.extension == "html" }
      .mapNotNull { file ->
        val yamlFile = Paths.get(file.toPath().parent.toString(), "${file.nameWithoutExtension}.yml").toFile()
        val fileName = file.name
        val yamlFileAbsolutePath = yamlFile.absolutePath
        if (!yamlFile.exists()) {
          logger.warn("Missing YAML file: $yamlFileAbsolutePath, unable to publish $fileName to WordPress")
          null
        } else {
          logger.debug("Loading $yamlFile")
          val attributes = yaml.load(FileInputStream(yamlFile)) as Map<*, *>
          logger.debug("Document attributes in the YAML file: $attributes")
          val slug = getSlug(attributes, yamlFileAbsolutePath, fileName)
          val title = getTitle(attributes, yamlFileAbsolutePath, fileName)
          if (slug != null && title != null) {
            // The terms assigned to the object in the post_tag taxonomy.
            val tags = getTags(attributes)
            DocumentAttributes(slug, title, tags, file.readText(Charsets.UTF_8))
          } else {
            null
          }
        }
      }
  }

  /**
   * Execute a request that returns JSON.
   */
  private fun <T> executeRequest(request: Request, mapper: (ResponseBody) -> T): T? {
    httpClient.newCall(request).execute().use {
      if (it.isSuccessful) {
        it.body.use { responseBody ->
          if (responseBody != null) {
            val contentType = responseBody.contentType()
            if (contentType != null) {
              if (contentType.type == "application" && contentType.subtype == "json") {
                try {
                  return mapper(responseBody)
                } catch (e: KlaxonException) {
                  logger.error("Unable to parse the response", e)
                }
              } else {
                logger.warn("Content-Type must be application/json")
              }
            } else {
              logger.warn("Content-Type is undefined")
            }
          } else {
            logger.warn("Response is empty")
          }
        }
      } else {
        logger.warn("Request is unsuccessful - {request: $request, code: ${it.code}, message: ${it.message}, response: ${it.body?.string()}}")
      }
    }
    return null
  }

  private fun getMandatoryString(attributes: Map<*, *>, name: String, yamlFilePath: String, fileName: String): String? {
    val value = attributes[name]
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

  private fun getTags(attributes: Map<*, *>): List<String> {
    val value = attributes["tags"] ?: return listOf()
    if (value is List<*>) {
      return value.filterIsInstance<String>()
    }
    return listOf()
  }

  private fun getTitle(attributes: Map<*, *>, yamlFilePath: String, fileName: String): String? {
    return getMandatoryString(attributes, "title", yamlFilePath, fileName)
  }

  private fun getSlug(attributes: Map<*, *>, yamlFilePath: String, fileName: String): String? {
    return getMandatoryString(attributes, "slug", yamlFilePath, fileName)
  }

  private fun httpClient(): OkHttpClient {
    val client = OkHttpClient.Builder()
      .authenticator(object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
          if (responseCount(response) >= 3) {
            return null // unable to authenticate for the third time, we give up...
          }
          val credential = Credentials.basic(connectionInfo.username, connectionInfo.password)
          return response.request.newBuilder().header("Authorization", credential).build()
        }
      })
      .connectTimeout(connectionInfo.connectTimeout)
      .writeTimeout(connectionInfo.writeTimeout)
      .readTimeout(connectionInfo.readTimeout)
    return client.build()
  }

  private fun responseCount(response: Response): Int {
    var count = 1
    var res = response.priorResponse
    while (res != null) {
      count++
      res = res.priorResponse
    }
    return count
  }
}
