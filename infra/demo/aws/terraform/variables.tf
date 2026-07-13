variable "region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2" # Seoul
}

variable "project" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "portfolio-demo"
}

variable "ami_id" {
  description = "docker + compose + 모노레포 스택 + demo-stack.service 가 baked 된 AMI"
  type        = string
}

variable "instance_type" {
  description = <<-EOT
    데모 스택을 돌릴 인스턴스 타입.
    실측 기반 사이징: JVM 앱 서비스 평균 ~390 MiB RSS (live fed-e2e 21개 컨테이너 측정).
    full 이 의도대로(iam/wms 앱 포함) 뜨면 JVM ~40개 ≈ 15.6 GiB + 백킹/관측 ~7 GiB ≈ 22 GiB steady.
    → 32GB(m6i.2xlarge) 로 충분, 64GB(m6i.4xlarge) 는 기동 스파이크까지 여유.
  EOT
  type        = string
  default     = "m6i.2xlarge"
}

# VPC / 서브넷은 "결정사항"이 아니다 — 모든 계정에 default VPC + 퍼블릭 서브넷이 있다.
# 아래 두 변수를 비워두면 main.tf 의 data source 가 default VPC 를 자동 탐색한다.
# 특정 VPC 를 쓰고 싶을 때만 채우면 된다.
variable "vpc_id" {
  description = "(선택) 보안그룹을 둘 VPC. 비우면 default VPC 자동 탐색"
  type        = string
  default     = ""
}

variable "subnet_id" {
  description = "(선택) 인스턴스를 둘 퍼블릭 서브넷. 비우면 default VPC 의 첫 서브넷 자동 선택"
  type        = string
  default     = ""
}

# stop 상태에서도 과금되는 상시 비용. AMI 스냅샷 크기(packer var.volume_gb) 아래로는
# 내릴 수 없다 — 두 값은 함께 움직여야 한다.
variable "root_volume_gb" {
  description = "루트 EBS gp3 크기(GB) — stop 상태에서도 과금되는 상시 비용"
  type        = number
  default     = 100
}

# default 를 주지 않는다. 기본값 "0.0.0.0/0" 은 SSH 를 전 세계에 여는 footgun 이었고,
# 아무도 눈치채지 못한 채 apply 되기 딱 좋다. 명시적으로 넘기게 강제한다.
variable "admin_ssh_cidr" {
  description = "SSH(22) 허용 CIDR — 반드시 본인 IP/32. 기본값 없음(명시 필수)"
  type        = string
}

variable "allowed_origin" {
  description = <<-EOT
    CORS 허용 오리진. **비워두면 사이트 자신의 CloudFront 도메인**이 쓰인다(권장) —
    그 값은 배포 시점에야 정해지므로 terraform 이 참조한다. 손으로 박으면 재생성마다
    썩는다(TASK-MONO-389 가 고친 결함이 정확히 그것이다).
    로컬에서 index.html 을 파일로 열어보려면 "*" 를 명시하라.
  EOT
  type        = string
  default     = ""
}

variable "idle_minutes" {
  description = "heartbeat 이 이 시간(분) 이상 끊기면 자동 종료"
  type        = number
  default     = 20
}

variable "max_runtime_minutes" {
  description = "가동 후 이 시간(분) 초과 시 무조건 종료 (안전장치)"
  type        = number
  default     = 180
}

# /start 는 인증 없는 공개 엔드포인트다(정적 사이트에 토큰을 숨길 곳이 없다).
# idle_minutes / max_runtime_minutes 는 반복 호출로 리셋되므로 지출 상한이 아니다.
# 이 값만이 실질적 상한이다: 월 누적 running 시간이 이를 넘으면 즉시 stop + /start 429.
# 600분(10시간) × m6i.2xlarge ≈ $5/월 — EBS 상시 $9 와 합쳐 월 $15 미만으로 묶인다.
variable "monthly_budget_minutes" {
  description = "월 누적 가동 상한(분). 초과 시 자동 종료 + /start 거절. 매월 1일 리셋"
  type        = number
  default     = 600
}
