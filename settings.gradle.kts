plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "slack-cv-bot-kotlin"

include("slack-cv-bot-receiver")
include("slack-cv-bot-handler")
include("schemas")
