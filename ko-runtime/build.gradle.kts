dependencies {
    api(project(":ko-annotations"))
    api(libs.spring.boot.starter.web)
    api(libs.spring.boot.starter.jdbc)
    api(libs.spring.boot.autoconfigure)
    implementation(libs.jackson.databind)
    implementation(libs.h2.database)
}
