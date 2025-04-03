package no.jpro.slack.cv

import com.slack.api.bolt.jetty.SlackAppServer


fun main() {

    val slackapp = SlackApp()


    val server = SlackAppServer(slackapp.app)
    server.start() // http://localhost:3000/slack/events
}

fun getEnvVariableOrThrow(variableName: String) = System.getenv().get(variableName)
    ?: throw Exception("$variableName not defined in environment variables")

fun getEnvVariableOrDefault(variableName: String, default: String) = System.getenv().get(variableName)
    ?: default

