plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("ko") {
            id = "dev.ko"
            implementationClass = "dev.ko.gradle.KoPlugin"
        }
    }
}
