# Jpro CV assistent Slackbot

Slackbot som gir leser cv, laster den inn til openai og gir deg mulighet til å snakke med den etterpå

## [slack-cv-bot-receiver](slack-cv-bot-receiver)

### Konfig

System variabler. 

- **OPENAI_API_KEY**: Accesskey til openai
- **SLACK_BOT_TOKEN**: Slack sikkerhetsopplegg. Finnes i config av slack app Features/OAuth & Permissions
- **SLACK_SIGNING_SECRET**: Slack sikkerhetsopplegg. Finnes i config av slack app under Settings/Basic Information
- **FLOWCASE_API_KEY**: Bearer token for cvparter/flowcase
- *LOGBACK_APPENDER*: Hvor skal logges skrives. STDOUT eller GCLOUD. Default til GCLOUD

Applikajsonen kjører på google cloud run https://console.cloud.google.com/run/detail/europe-north2/cvbot/
og bygges og deployes via github actions

### Implementasjon

Slack api for kotlin
https://tools.slack.dev/java-slack-sdk/guides/getting-started-with-bolt#run-kotlin

Applikasjonen bruker assistant og threads fra openai for å beholde en slags tilstand. Dette gjøres ved at openai
threadid tas vare på i en slack tråd

### Lokal utvikling

Appen kan kjøres lokalt og eksponeres feks via https://ngrok.com/ 

`ngrok http 3000`

Urler må klippes ut og limes inn i slack config for appen under
- Features/Event Subscriptions https://api.slack.com/apps/A08F4EW0UV9/event-subscriptions
- Features/Slash commands https://api.slack.com/apps/A08F4EW0UV9/slash-commands?

event apiet finnes på <din-url>/slack/events

## [infrastructure](infrastructure)

State bucket set up
```shell
gcloud storage buckets create "gs://terraform-state-slack-cv-bot-$(gcloud config get project)" --location europe-west1 --public-access-prevention
gcloud storage buckets update "gs://terraform-state-slack-cv-bot-$(gcloud config get project)" --versioning
```
