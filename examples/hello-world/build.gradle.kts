plugins {
    id("dev.ko")
}

ko {
    appName.set("hello-world")
}

dependencies {
    implementation(project(":ko-annotations"))
    annotationProcessor(project(":ko-processor"))
}
