import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.omotolani98"
version = providers.fileContents(
    rootProject.layout.projectDirectory.file("../gradle.properties")
).asText.map { text ->
    text.lines().first { it.startsWith("version=") }.substringAfter("version=")
}.get()

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

tasks.withType<Jar> {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

gradlePlugin {
    plugins {
        create("ko") {
            id = "io.github.omotolani98.ko"
            implementationClass = "dev.ko.gradle.KoPlugin"
        }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    configure(GradlePlugin(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))

    pom {
        name.set("Ko Gradle Plugin")
        description.set("Kọ́ Framework — Gradle plugin for type-safe distributed systems for Java")
        url.set("https://github.com/Omotolani98/ko")
        licenses {
            license {
                name.set("MPL-2.0")
                url.set("https://www.mozilla.org/en-US/MPL/2.0/")
            }
        }
        developers {
            developer {
                id.set("Omotolani98")
                name.set("Omotolani98")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/Omotolani98/ko.git")
            developerConnection.set("scm:git:ssh://github.com/Omotolani98/ko.git")
            url.set("https://github.com/Omotolani98/ko")
        }
    }
}
