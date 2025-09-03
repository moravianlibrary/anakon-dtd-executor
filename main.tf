terraform {
  required_version = "~>1.11"
  required_providers {
    # https://registry.terraform.io/providers/kreuzwerker/docker/latest/docs
    docker = {
      source  = "kreuzwerker/docker"
      version = "3.6.2"
    }
  }
}

variable "APP_ENV_NAME" {
  type    = string
  default = "test"
}

variable "APP_DB_HOST" {
  type = string
}

variable "APP_DB_PORT" {
  type    = string
  default = "5432"
}

variable "APP_DB_NAME" {
  type = string
}

variable "APP_DB_USERNAME" {
  type = string
}

variable "APP_DB_PASSWORD" {
  type = string
}

variable "APP_DYNAMIC_CONFIG_FILE" {
  type = string
}

variable "APP_PROCESS_EXECUTION_DIR" {
  type = string
}

variable "APP_PROCESS_DEFINITION_DIR" {
  type = string
}

variable "docker_host_uri" {
  type = string
}

variable "docker_image" {
  type = string
}

variable "docker_container_name" {
  type = string
}

variable "ghcr_username" {
  type = string
}

variable "ghcr_token" {
  type = string
}

provider "docker" {
  host     = var.docker_host_uri
  ssh_opts = ["-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null"]
  registry_auth {
    address  = "ghcr.io"
    username = var.ghcr_username
    password = var.ghcr_token
  }
}

# Creating anakon_dtd_executor Docker Image data source
data "docker_registry_image" "anakon_dtd_executor" {
  name = var.docker_image
}

# Creating anakon_dtd_executor Docker Image
# with the `latest` as Tag
resource "docker_image" "anakon_dtd_executor" {
  name          = data.docker_registry_image.anakon_dtd_executor.name
  pull_triggers = [data.docker_registry_image.anakon_dtd_executor.sha256_digest]
}

# Create Docker Container using the anakon_dtd_executor image.
resource "docker_container" "anakon_dtd_executor" {
  count    = 1
  image    = docker_image.anakon_dtd_executor.image_id
  name     = var.docker_container_name
  must_run = true
  restart  = "no" # "on-failure" is better solution
  env = [
    "APP_ENV_NAME=${var.APP_ENV_NAME}",
    "APP_DB_HOST=${var.APP_DB_HOST}",
    "APP_DB_PORT=${var.APP_DB_PORT}",
    "APP_DB_NAME=${var.APP_DB_NAME}",
    "APP_DB_USERNAME=${var.APP_DB_USERNAME}",
    "APP_DB_PASSWORD=${var.APP_DB_PASSWORD}",
    "APP_PROCESS_EXECUTION_DIR=${var.APP_PROCESS_EXECUTION_DIR}",
    "APP_PROCESS_DEFINITION_DIR=${var.APP_PROCESS_DEFINITION_DIR}",
    "APP_DYNAMIC_CONFIG_FILE=${var.APP_DYNAMIC_CONFIG_FILE}",
  ]

  mounts {
    # target = dirname(var.APP_DYNAMIC_CONFIG_FILE)
    # target = var.APP_PROCESS_EXECUTION_DIR
    target = "/app/dtd-executor/data"
    type   = "bind"
    source = "/srv/docker/anakon/${var.APP_ENV_NAME}"
    # source = "/srv/docker/anakon/${var.APP_ENV_NAME}/process_executions"
  }
}
