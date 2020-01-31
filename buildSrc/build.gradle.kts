plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
  jcenter()
}

dependencies {
  implementation("com.amazonaws:aws-java-sdk-s3:1.11.714")
  implementation(gradleApi())
}

gradlePlugin {
  plugins {
    create("s3") {
      id = "com.neo4j.gradle.s3.S3Plugin"
      implementationClass = "com.neo4j.gradle.s3.S3Plugin"
    }
  }
}
