terraform {
  backend "gcs" {
    prefix = "terraform/state"
  }
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 5.34.0"
    }
  }
}

provider "google" {
  project = var.google_cloud_project_id
}

data "google_project" "project" {
}

data "google_secret_manager_secret" "OpenAPI_slack_key" {
  secret_id = "OpenAPI_slack_key"
}

data "google_secret_manager_secret" "CVBOT_SLACK_TOKEN" {
  secret_id = "CVBOT_SLACK_TOKEN"
}

data "google_secret_manager_secret" "CVBOT_SLACK_SIGNING_SECRET" {
  secret_id = "CVBOT_SLACK_SIGNING_SECRET"
}

data "google_secret_manager_secret" "CVBOT_FLOWCASE_API_KEY" {
  secret_id = "CVBOT_FLOWCASE_API_KEY"
}

data "google_artifact_registry_docker_image" "slack-cv-bot-receiver" {
  location      = google_artifact_registry_repository.slack-cv-bot.location
  repository_id = google_artifact_registry_repository.slack-cv-bot.repository_id
  image_name    = "slack-cv-bot-receiver"
}

resource "google_artifact_registry_repository" "slack-cv-bot" {
  repository_id = "slack-cv-bot"
  description   = "docker repository for https://github.com/jprofessionals/slack-cv-bot"
  location      = "europe"
  format        = "DOCKER"

  cleanup_policies {
    id = "keep-most-recent"
    action = "KEEP"
    most_recent_versions {
      keep_count = 6
    }
  }
}

resource "google_service_account" "slack-cv-bot-github-actions" {
  account_id = "slack-cv-bot-github-actions"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_artifactregistry" {
  project = var.google_cloud_project_id
  role    = "roles/artifactregistry.admin"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_run" {
  project = var.google_cloud_project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_storage" {
  project = var.google_cloud_project_id
  role    = "roles/storage.objectAdmin"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_iam_serviceAccountAdmin" {
  project = var.google_cloud_project_id
  role    = "roles/iam.serviceAccountAdmin"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_iam_serviceAccountUser" {
  project = var.google_cloud_project_id
  role    = "roles/iam.serviceAccountUser"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_iam_roleAdmin" {
  project = var.google_cloud_project_id
  role    = "roles/iam.roleAdmin"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_resourcemanager" {
  project = var.google_cloud_project_id
  role    = "roles/resourcemanager.projectIamAdmin"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_pubsub" {
  project = var.google_cloud_project_id
  role    = "roles/pubsub.editor"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_project_iam_member" "slack-cv-bot-github-actions_secretmanager" {
  project = var.google_cloud_project_id
  role    = "roles/secretmanager.viewer"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-github-actions.email}"
}

resource "google_service_account" "slack-cv-bot-receiver" {
  account_id = "slack-cv-bot-receiver"
}

resource "google_project_iam_member" "slack-cv-bot-receiver_logging" {
  project = var.google_cloud_project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.slack-cv-bot-receiver.email}"
}

resource "google_secret_manager_secret_iam_member" "OpenAPI_slack_key-access" {
  secret_id = data.google_secret_manager_secret.OpenAPI_slack_key.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.slack-cv-bot-receiver.email}"
}

resource "google_secret_manager_secret_iam_member" "CVBOT_SLACK_TOKEN-access" {
  secret_id = data.google_secret_manager_secret.CVBOT_SLACK_TOKEN.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.slack-cv-bot-receiver.email}"
}

resource "google_secret_manager_secret_iam_member" "CVBOT_SLACK_SIGNING_SECRET-access" {
  secret_id = data.google_secret_manager_secret.CVBOT_SLACK_SIGNING_SECRET.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.slack-cv-bot-receiver.email}"
}

resource "google_secret_manager_secret_iam_member" "CVBOT_FLOWCASE_API_KEY-access" {
  secret_id = data.google_secret_manager_secret.CVBOT_FLOWCASE_API_KEY.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.slack-cv-bot-receiver.email}"
}

resource "google_cloud_run_v2_service" "slack-cv-bot-receiver" {
  name     = "slack-cv-bot-receiver"
  location = "europe-west1"
  ingress = "INGRESS_TRAFFIC_ALL"


  template {
    service_account = google_service_account.slack-cv-bot-receiver.email

    scaling {
      min_instance_count = 0
      max_instance_count = 1
    }

    containers {
      name = "slack-cv-bot-receiver"
      image = data.google_artifact_registry_docker_image.slack-cv-bot-receiver.self_link

      ports {
        container_port = 3000
      }

      env {
        name = "OPENAI_API_KEY"
        value_source {
          secret_key_ref {
            secret = data.google_secret_manager_secret.OpenAPI_slack_key.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "SLACK_BOT_TOKEN"
        value_source {
          secret_key_ref {
            secret = data.google_secret_manager_secret.CVBOT_SLACK_TOKEN.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "SLACK_SIGNING_SECRET"
        value_source {
          secret_key_ref {
            secret = data.google_secret_manager_secret.CVBOT_SLACK_SIGNING_SECRET.secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "FLOWCASE_API_KEY"
        value_source {
          secret_key_ref {
            secret = data.google_secret_manager_secret.CVBOT_FLOWCASE_API_KEY.secret_id
            version = "latest"
          }
        }
      }
    }
  }
}

resource "google_cloud_run_service_iam_binding" "default" {
  location = google_cloud_run_v2_service.slack-cv-bot-receiver.location
  service  = google_cloud_run_v2_service.slack-cv-bot-receiver.name
  role     = "roles/run.invoker"
  members = [
    "allUsers"
  ]
}

resource "google_pubsub_topic" "slack-events" {
  name = "slack-events"

  message_retention_duration = "${31*24*60*60}s"
  message_storage_policy {
    allowed_persistence_regions = [var.google_cloud_region]
  }
}
