pluginManagement {
    includeBuild("ko-gradle-plugin")
}

rootProject.name = "ko"

include("ko-annotations")
include("ko-processor")
include("ko-runtime")
include("ko-test")
include("examples:hello-world")
