plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "dev.ko"
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

gradlePlugin {
    plugins {
        create("ko") {
            id = "dev.ko"
            implementationClass = "dev.ko.gradle.KoPlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
