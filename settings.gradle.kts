plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
rootProject.name = "slack-cv-bot-kotlin"

include("slack-cv-bot-receiver")
include("slack-cv-bot-handler")
include("schemas")
