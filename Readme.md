# Jpro CV assistent Slackbot

Slackbot som gir leser cv, og bruker OpenAI til å komme med detaljerte tilbakemeldinger og forbedringsforslag

### Logs

https://cloudlogging.app.goo.gl/4hQHqV9Nwh8R5Fy58

## [slack-cv-bot-receiver](slack-cv-bot-receiver)

### Konfig

System variabler. 

- **SLACK_BOT_TOKEN**: Slack sikkerhetsopplegg. Finnes i config av slack app Features/OAuth & Permissions
- **SLACK_SIGNING_SECRET**: Slack sikkerhetsopplegg. Finnes i config av slack app under Settings/Basic Information
- *LOGBACK_APPENDER*: Hvor skal logges skrives. STDOUT eller GCLOUD. Default til GCLOUD

Applikasjonen kjører på google cloud run https://console.cloud.google.com/run/detail/europe-west1/slack-cv-bot-receiver
og bygges og deployes via github actions

### Implementasjon

Slack api for kotlin
https://tools.slack.dev/java-slack-sdk/guides/getting-started-with-bolt#run-kotlin

Applikasjonen tar imot kall fra Slack og publiserer til Pubsub

### Lokal utvikling

Appen kan kjøres lokalt og eksponeres feks via https://ngrok.com/ 

`ngrok http 3000`

Urler må klippes ut og limes inn i slack config for appen under
- Features/Event Subscriptions https://api.slack.com/apps/A08F4EW0UV9/event-subscriptions
- Features/Slash commands https://api.slack.com/apps/A08F4EW0UV9/slash-commands?

event apiet finnes på <din-url>/slack/events

## [slack-cv-bot-handler](slack-cv-bot-handler)

### Konfig

System variabler. 

- **OPENAI_API_KEY**: Accesskey til openai
- **SLACK_BOT_TOKEN**: Slack sikkerhetsopplegg. Finnes i config av slack app Features/OAuth & Permissions
- **FLOWCASE_API_KEY**: Bearer token for cvparter/flowcase
- *LOGBACK_APPENDER*: Hvor skal logges skrives. STDOUT eller GCLOUD. Default til GCLOUD

Applikasjonen kjører på google cloud run https://console.cloud.google.com/run/detail/europe-west1/slack-cv-bot-handler
og bygges og deployes via github actions

### Implementasjon

Applikasjonen subscriber til Pubsub og håndterer CV-review forespørsler ved å snakke med Flowcase, OpenAI og Slack

## [infrastructure](infrastructure)

Setter opp infrastrukturen for prosjektet med Terraform

### State bucket set up
```shell
gcloud storage buckets create "gs://terraform-state-slack-cv-bot-$(gcloud config get project)" --location europe-west1 --public-access-prevention
gcloud storage buckets update "gs://terraform-state-slack-cv-bot-$(gcloud config get project)" --versioning
```
