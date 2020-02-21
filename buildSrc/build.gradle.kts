plugins {
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
  implementation(gradleApi())
}

gradlePlugin {
  plugins {
    create("s3") {
      id = "com.neo4j.gradle.s3.S3Plugin"
      implementationClass = "com.neo4j.gradle.s3.S3Plugin"
    }
    create("asciidoctorTableOfContents") {
      id = "com.neo4j.gradle.asciidoctor.AsciidoctorTableOfContentsPlugin"
      implementationClass = "com.neo4j.gradle.asciidoctor.AsciidoctorTableOfContentsPlugin"
    }
    create("wordpress") {
      id = "com.neo4j.gradle.wordpress.WordPressPlugin"
      implementationClass = "com.neo4j.gradle.wordpress.WordPressPlugin"
    }
  }
}
