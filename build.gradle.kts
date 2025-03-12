plugins {
    application
    kotlin("jvm") version "2.1.10"
    id("com.google.cloud.tools.jib") version "3.4.4"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    implementation ("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.17")
    implementation("com.google.cloud:google-cloud-logging-logback:0.131.11-alpha")
    // Openai
    implementation("com.aallam.openai:openai-client:4.0.1")
    runtimeOnly("io.ktor:ktor-client-java:3.1.0")

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
        image = "europe-north2-docker.pkg.dev/my-page-jpro-test/jpro-slackbot/cvbot"
        setCredHelper("gcr")
    }
    container{
        ports= listOf("3000")
    }

}
