plugins {
    id("io.github.omotolani98.ko")
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

ko {
    appName = "hello-world"
}

dependencies {
    testImplementation(project(":ko-test"))
}

// In-repo development: substitute Maven coordinates with local project modules
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.github.omotolani98:ko-annotations")).using(project(":ko-annotations"))
        substitute(module("io.github.omotolani98:ko-runtime")).using(project(":ko-runtime"))
        substitute(module("io.github.omotolani98:ko-processor")).using(project(":ko-processor"))
    }
}

springBoot {
    mainClass.set("com.example.Application")
}
