terraform {
  required_version = ">= 1.6"
  required_providers {
    # Pinned to the major this config was actually validated against.
    # An open ">= 5.40" silently resolved to 6.x, which carries breaking changes.
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.8"
    }
  }
}

provider "aws" {
  region = var.region
}
