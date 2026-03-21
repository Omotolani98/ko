import com.vanniktech.maven.publish.SonatypeHost
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    alias(libs.plugins.maven.publish) apply false
}

subprojects {
    if (project.path.startsWith(":examples")) return@subprojects

    apply(plugin = "java-library")
    apply(plugin = "com.vanniktech.maven.publish")

    group = rootProject.property("group") as String
    version = rootProject.property("version") as String

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        signAllPublications()
        configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))

        pom {
            name.set("Ko ${project.name}")
            description.set("Kọ́ Framework — type-safe distributed systems for Java")
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
}
