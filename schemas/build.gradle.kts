plugins {
    kotlin("jvm") version "2.1.20"
}

group = "no.jpro.slack.cv"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    api("org.apache.avro:avro:1.12.0")
}

tasks.test {
    useJUnitPlatform()
}

val avroTools by configurations.creating
dependencies {
    avroTools("org.apache.avro:avro-tools:1.12.0")
}

val generateAvro = tasks.register<JavaExec>("generateAvro") {
    val inputDir = file("src/main/resources/avro")
    val outputDir = layout.buildDirectory.dir("generated-src/avro").get().asFile

    inputs.dir(inputDir)
    outputs.dir(outputDir)

    doFirst {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
    }

    mainClass.set("org.apache.avro.tool.Main")
    classpath = avroTools
    args = listOf("compile", "schema", inputDir.absolutePath, outputDir.absolutePath, "-string")
}

// Include the generated sources in the main source set
sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated-src/avro").get().asFile)

tasks.named("compileJava") {
    dependsOn(generateAvro)
}
