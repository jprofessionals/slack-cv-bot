plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    id("com.google.cloud.tools.jib") version "3.4.5"
}

group = "no.jpro.slack.cv"
version = "1.0-SNAPSHOT"
val slack_bolt_version by extra("1.45.3")


repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.slack.api:bolt-jetty:$slack_bolt_version")
    implementation("com.slack.api:slack-api-client-kotlin-extension:$slack_bolt_version")
    implementation("com.slack.api:slack-api-model-kotlin-extension:$slack_bolt_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Json stuff
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.0")

    implementation ("io.github.oshai:kotlin-logging-jvm:7.0.7")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // Google Cloud
    implementation(platform("com.google.cloud:libraries-bom:26.61.0"))
    implementation("com.google.cloud:google-cloud-logging-logback")
    implementation("com.google.cloud:google-cloud-pubsub")

    // Openai
    implementation ("io.github.sashirestela:simple-openai:3.21.0")
    /* OkHttp dependency is optional if you decide to use it with simple-openai */
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(project(":schemas"))
}

application {
    mainClass = "no.jpro.slack.cv.MainKt"
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

jib {
    to {
        image = "europe-docker.pkg.dev/my-page-jpro-test/slack-cv-bot/slack-cv-bot-receiver"
        setCredHelper("gcr")
    }
    container{
        ports= listOf("3000")
    }

}
