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

    🔴 과거 산정("JVM ~40개 ≈ 15.6 GiB + 백킹/관측 ~7 GiB ≈ 22 GiB steady → 32GB 로 충분")은
    **틀렸다. TASK-MONO-552 AC-0 이 라이브 호스트에서 반증했다**:
      · 정상 상태 실측 = 31.5 GiB 중 **약 29 GiB 사용 / MemAvailable 2.4 GiB**
      · 그 여유로는 **어떤 추가 작업이든**(시드 · 컨테이너 재생성 · 동시 접속) 방아쇠가 된다
      · 실제로 3회 중 3회 굳었다. 마지막 회차 실측:
          MemAvailable 2,387M → **113M** · Cached 2,485M → 377M
          pressure/memory full **0.00 → 41.16** · pgmajfault **+436k / 5분**
          수집기 스케줄 지연 **196초** · load 356 (runnable 4 = 나머지는 D 상태)
      · 스왑이 없어 OOM kill 이 아니라 **파일 페이지 thrashing** 으로 간다 ⇒ 커널 로그가 조용하다.

    ⇒ 사이징 기준은 "합계 추정" 이 아니라 **정상 상태 MemAvailable 6~8 GiB 확보** 다.

    r6i.2xlarge(8 vCPU / 64 GiB)를 쓴다. m6i.4xlarge 도 64 GiB 지만 vCPU 가 16 이라 더 비싸고,
    **AC-0 이 CPU 는 병목이 아니라고 측정했다**(웜업 구간 외 pressure/cpu some 10~15%).
    메모리만 사면 된다.

    🔵 바꾼 뒤에는 `infra/demo/wedge-collector.sh` 로 **정상 상태 MemAvailable 을 다시 재라**.
       같은 계측기라야 before/after 가 비교된다.
  EOT
  type        = string
  default     = "r6i.2xlarge"
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

variable "allowed_origins" {
  description = <<-EOT
    CORS 허용 오리진 목록. **이것이 목록의 전부다** (TASK-MONO-579).

    🔴 이전 판에서는 "**추가** 허용할" 목록이었다 — 사이트 자신의 CloudFront 도메인이
    `local.cors_allowed_origins` 에 **항상 참조로** 붙었고 이 변수는 그 위에 더해졌다.
    `ADR-MONO-067` D3 으로 CloudFront 판을 폐기하면서 그 자동 항목이 사라졌고,
    **이제 허용 목록은 이 변수 하나가 전부다.**

    여기 적는 것은 론처가 실제로 서빙되는 오리진이다. 예:
      allowed_origins = ["https://kanggle-portfolio.vercel.app"]

    🔴 AWS 가 발급하는 주소(*.cloudfront.net · *.execute-api.*)를 손으로 박지 마라 —
    재생성마다 썩는다(TASK-MONO-389 가 고친 결함). 그 명제는
    infra/demo/verify-demo-wrapper.sh (z9)(2) 가 계속 지킨다.

    🔵 문자열 하나가 아니라 목록인 이유(TASK-MONO-557): 호스팅을 옮기는 동안 **두 오리진을
    동시에** 허용할 수 있어야 한다. 그래야 옮기는 과정에 론처가 죽는 창이 생기지 않는다.

    로컬에서 index.html 을 파일로 열어보려면 ["*"] 를 명시하라.
  EOT
  type        = list(string)

  # 🔴🔴 이 변수의 안전장치는 기본값이 아니라 **아래 validation** 이다. CloudFront 참조가
  # 사라진 지금 빈 목록은 "허용 오리진 0개" = 브라우저가 컨트롤 API 를 못 부른다 =
  # **론처의 Start 버튼이 죽는다.** 그런데 그 실패는 plan 에서 안 보이고 런타임에 조용히
  # 온다. validation 이 그 실패를 plan 으로 끌어온다.
  #
  # 🔵 기본값에 Vercel 주소를 박지 않는 이유: 이 모듈은 자기가 어디에 서빙되는지 모른다.
  # 박아 두면 **틀린 값이 조용히 통과**하고, 그건 빈 값보다 나쁘다 — 빈 값은 아래에서
  # 큰 소리로 죽는다.
  default = []

  validation {
    condition     = length(var.allowed_origins) > 0
    error_message = "allowed_origins 가 비어 있습니다. CloudFront 자동 포함이 폐기된 뒤(TASK-MONO-579) 이 목록이 CORS 의 전부이므로, 빈 목록은 론처의 Start 버튼이 죽는다는 뜻입니다. 론처가 서빙되는 오리진을 적으세요."
  }

  validation {
    # "*" 는 로컬에서 file:// 로 열어보는 용도로 문서화되어 있다 — 계속 허용한다.
    condition     = alltrue([for o in var.allowed_origins : o == "*" || startswith(o, "https://")])
    error_message = "allowed_origins 의 각 항목은 https:// 로 시작하거나 * 여야 합니다. 론처는 HTTPS 로 서빙됩니다."
  }

  validation {
    # 브라우저가 보내는 Origin 헤더에는 끝 슬래시가 없다. 붙여 두면 **아무것과도 매칭되지
    # 않는데 plan 은 통과한다** — site/build.sh 가 DEMO_API_BASE 에 대해 이미 같은 검사를
    # 하는 것과 같은 함정이다.
    condition     = alltrue([for o in var.allowed_origins : !endswith(o, "/")])
    error_message = "allowed_origins 항목 끝에 슬래시를 붙이지 마세요. 브라우저의 Origin 헤더에는 끝 슬래시가 없어 어느 것과도 매칭되지 않습니다."
  }
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
