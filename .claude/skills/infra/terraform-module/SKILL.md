---
name: terraform-module
description: Terraform module patterns
category: infra
---

# Skill: Terraform Module

Patterns for Terraform infrastructure-as-code in this repository.

Prerequisite: read `specs/platform/deployment-policy.md` before using this skill.

---

## Module Structure

```
terraform/
├── modules/
│   ├── vpc/
│   ├── rds/
│   ├── redis/
│   ├── kafka/
│   └── eks/
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   └── prod/
│       ├── main.tf
│       ├── variables.tf
│       └── terraform.tfvars
└── backend.tf
```

---

## Module Pattern

Each module encapsulates one infrastructure component.

```hcl
# modules/rds/main.tf
resource "aws_db_instance" "this" {
  identifier     = "${var.project}-${var.service}-${var.environment}"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.instance_class
  db_name        = var.db_name
  username       = var.db_username
  password       = var.db_password

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage

  vpc_security_group_ids = [var.security_group_id]
  db_subnet_group_name   = var.subnet_group_name

  skip_final_snapshot = var.environment != "prod"

  tags = {
    Project     = var.project
    Service     = var.service
    Environment = var.environment
  }
}
```

```hcl
# modules/rds/variables.tf
variable "project" { type = string }
variable "service" { type = string }
variable "environment" { type = string }
variable "instance_class" { type = string, default = "db.t3.micro" }
variable "db_name" { type = string }
variable "db_username" { type = string }
variable "db_password" { type = string, sensitive = true }
variable "allocated_storage" { type = number, default = 20 }
variable "max_allocated_storage" { type = number, default = 100 }
variable "security_group_id" { type = string }
variable "subnet_group_name" { type = string }
```

```hcl
# modules/rds/outputs.tf
output "endpoint" { value = aws_db_instance.this.endpoint }
output "port" { value = aws_db_instance.this.port }
```

---

## Environment Usage

```hcl
# environments/dev/main.tf
module "auth_db" {
  source = "../../modules/rds"

  project         = "ecommerce"
  service         = "auth"
  environment     = "dev"
  db_name         = "auth_db"
  db_username     = "auth_user"
  db_password     = var.auth_db_password
  instance_class  = "db.t3.micro"
  security_group_id = module.vpc.db_security_group_id
  subnet_group_name = module.vpc.db_subnet_group_name
}
```

---

## Naming Convention

Pattern: `{project}-{service}-{environment}`

Examples:
- `ecommerce-auth-dev`
- `ecommerce-order-prod`

---

## State Management

```hcl
# backend.tf
terraform {
  backend "s3" {
    bucket = "ecommerce-terraform-state"
    key    = "dev/terraform.tfstate"
    region = "ap-northeast-2"
  }
}
```

---

## Rules

- One module per infrastructure component.
- Environment-specific values in `terraform.tfvars` — never hardcoded.
- Sensitive values (passwords, keys) use `sensitive = true`.
- Always tag resources with project, service, and environment.
- Use `skip_final_snapshot = false` in production.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Hardcoded values in modules | Use variables with sensible defaults |
| Secrets in `terraform.tfvars` committed to git | Use environment variables or secrets manager |
| No tags on resources | Always tag for cost tracking and identification |
| Missing output values | Export endpoints and IDs for downstream modules |
| State file not locked | Use S3 backend with DynamoDB locking |
