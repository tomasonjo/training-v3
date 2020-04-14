plugins {
  id("com.gradle.plugin-publish") version "0.11.0"
  `java-gradle-plugin`
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
  jcenter()
}

dependencies {
  implementation("org.asciidoctor:asciidoctorj:2.2.0")
  implementation("com.amazonaws:aws-java-sdk-s3:1.11.714")
  implementation("com.squareup.okhttp3:okhttp:4.4.0")
  implementation("org.yaml:snakeyaml:1.25")
  implementation("com.beust:klaxon:5.0.1")
  implementation(gradleApi())
}

version = "0.0.1"

val pluginsEnabled = project.findProperty("plugins-enabled") as String?

fun isPluginEnabled(name: String): Boolean = pluginsEnabled == null || pluginsEnabled.isEmpty() || (pluginsEnabled.contains(name))

gradlePlugin {
  plugins {
    if (isPluginEnabled("s3")) {
      create("s3Plugin") {
        id = "com.neo4j.gradle.s3.S3Plugin"
        implementationClass = "com.neo4j.gradle.s3.S3Plugin"
      }
    }
    if (isPluginEnabled("asciidoctorModuleDescriptor")) {
      create("asciidoctorModuleDescriptorPlugin") {
        id = "com.neo4j.gradle.asciidoctor.AsciidoctorModuleDescriptorPlugin"
        implementationClass = "com.neo4j.gradle.asciidoctor.AsciidoctorModuleDescriptorPlugin"
      }
    }
    if (isPluginEnabled("wordpress")) {
      create("wordpressPlugin") {
        id = "com.neo4j.gradle.wordpress.WordPressPlugin"
        implementationClass = "com.neo4j.gradle.wordpress.WordPressPlugin"
        version = "0.0.2"
      }
    }
  }
}

pluginBundle {
  website = "https://neo4j.com/"
  vcsUrl = "https://github.com/neo4j-contrib/training-v3"

  (plugins) {
    if (isPluginEnabled("s3")) {
      "s3Plugin" {
        id = "com.neo4j.gradle.s3.S3Plugin"
        displayName = "Publish files to Amazon S3"
        description = "A plugin to publish files to Amazon S3"
        version = "0.0.1"
        tags = listOf("s3", "publish", "files")
      }
    }
    // should be a private plugin
    if (isPluginEnabled("asciidoctorModuleDescriptor")) {
      "asciidoctorModuleDescriptorPlugin" {
        id = "com.neo4j.gradle.asciidoctor.AsciidoctorModuleDescriptorPlugin"
        displayName = "Generate a very specific module descriptor from an AsciiDoc file"
        description = "A plugin to generate a very specific module descriptor file in a YAML format from and AsciiDoc file."
        version = "0.0.1"
        tags = listOf("neo4j")
      }
    }
    if (isPluginEnabled("wordpress")) {
      "wordpressPlugin" {
        id = "com.neo4j.gradle.wordpress.WordPressPlugin"
        displayName = "Publish posts and pages to WordPress"
        description = "A plugin to publish posts or pages to WordPress from an HTML file and a YAML file that contains metadata"
        version = "0.0.2"
        tags = listOf("wordpress", "publish", "posts", "pages")
      }
    }
  }
}
