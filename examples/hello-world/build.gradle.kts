plugins {
    java
    alias(libs.plugins.spring.boot)
}

group = "dev.ko.examples"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ko-annotations"))
    implementation(project(":ko-runtime"))
    annotationProcessor(project(":ko-processor"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Ako.app.name=hello-world"
    ))
    options.encoding = "UTF-8"
}

springBoot {
    mainClass.set("com.example.Application")
}
