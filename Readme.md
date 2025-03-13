# Jpro CV assistent Slackbot

Slackbot som gir leser cv, laster den inn til openai og gir deg mulighet til å snakke med den etterpå

## Konfig

System variabler

- **OPENAI_API_KEY**: Accesskey til openai
- **SLACK_BOT_TOKEN**: Slack sikkerhetsopplegg. Finnes i config av slack app Features/OAuth & Permissions
- **SLACK_SIGNING_SECRET**: Slack sikkerhetsopplegg. Finnes i config av slack app under Settings/Basic Information
- *LOGBACK_APPENDER*: Hvor skal logges skrives. STDOUT eller GCLOUD. Default til GCLOUD

Applikajsonen kjører på google cloud run https://console.cloud.google.com/run/detail/europe-north2/cvbot/
og bygges og deployes via github actions

## Implementasjon

Slack api for kotlin
https://tools.slack.dev/java-slack-sdk/guides/getting-started-with-bolt#run-kotlin

Applikasjonen bruker assistant og threads fra openai for å beholde en slags tilstand. Dette gjøres ved at openai
threadid tas vare på i en slack tråd