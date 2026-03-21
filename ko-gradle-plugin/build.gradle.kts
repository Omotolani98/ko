plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
}

group = "io.github.omotolani98"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
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

signing {
    sign(publishing.publications)
}

publishing {
    repositories {
        mavenLocal()
    }
}
