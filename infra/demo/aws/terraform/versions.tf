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
    # TASK-MONO-647 — data.external.ami_bundle_capability 가 쓴다. 저장소에서 계산한
    # 「이 AMI 가 묶음 기동을 아는가」를 Lambda env 로 나르는 유일한 경로다.
    external = {
      source  = "hashicorp/external"
      version = "~> 2.3"
    }
  }
}

provider "aws" {
  region = var.region
}
