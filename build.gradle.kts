plugins {
    kotlin("jvm") version "2.1.10"
}

group = "no.jpro.slack.cv"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.slack.api:bolt-jetty:1.45.3")
    implementation("com.slack.api:slack-api-client-kotlin-extension:1.45.3")
    implementation("com.slack.api:slack-api-model-kotlin-extension:1.45.3")
    implementation("org.slf4j:slf4j-simple:2.0.17")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}