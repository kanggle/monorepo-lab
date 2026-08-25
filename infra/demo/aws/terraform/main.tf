# ---------------------------------------------------------------------------
# VPC / 서브넷 자동 탐색 — 사용자가 정할 것이 없다.
# 모든 AWS 계정에는 default VPC + AZ 별 퍼블릭 서브넷이 기본 제공된다.
# var.vpc_id / var.subnet_id 를 채우면 그쪽이 우선한다.
# ---------------------------------------------------------------------------
data "aws_vpc" "default" {
  count   = var.vpc_id == "" ? 1 : 0
  default = true
}

data "aws_subnets" "default" {
  count = var.subnet_id == "" ? 1 : 0
  filter {
    name   = "vpc-id"
    values = [var.vpc_id != "" ? var.vpc_id : data.aws_vpc.default[0].id]
  }
}

locals {
  name        = var.project
  beat_param  = "/${var.project}/last-heartbeat"
  start_param = "/${var.project}/started-at"
  usage_param = "/${var.project}/monthly-usage"
  # 도메인별 헬스 스냅샷(TASK-MONO-477). 인스턴스가 demo-status.sh 로 주기 발행하고
  # Lambda /domains 는 읽기만 한다 — SSM SendCommand 는 비동기라 매 요청 왕복이 취약하다.
  health_param = "/${var.project}/domains-health"

  vpc_id    = var.vpc_id != "" ? var.vpc_id : data.aws_vpc.default[0].id
  subnet_id = var.subnet_id != "" ? var.subnet_id : data.aws_subnets.default[0].ids[0]

  # CORS 허용 오리진 (TASK-MONO-557 → TASK-MONO-579).
  #
  # 이전 판은 사이트 자신의 **CloudFront 오리진을 항상 참조로** 포함하고, 다른 곳에서
  # 서빙되는 사본(Vercel 등)만 `var.allowed_origins` 로 더했다. 그 CloudFront 배포가
  # `ADR-MONO-067` D3 으로 폐기되면서(론처의 집 = Vercel 하나) **참조할 대상이 없어졌다.**
  #
  # 🔴 그런데 그 참조는 **구멍 하나를 가려 주고 있었다** — 목록이 항상 최소 1개였던 것은
  #    CloudFront 덕분이지 규칙 덕분이 아니었다. 그것을 치우면 빈 `allowed_origins` 가
  #    **plan 을 통과하고**, 실패는 런타임에 **론처의 Start 버튼이 조용히 죽는 모양**으로 온다.
  #    (그리고 `terraform.tfvars.example` 은 바로 그 빈 목록을 예시로 권하고 있었다.)
  #
  # 🔵 그래서 같은 변경에서 `var.allowed_origins` 에 **fail-closed validation** 을 걸었다
  #    (variables.tf). 실패 시점이 **런타임 → plan** 으로 옮겨진다. 여기서 다시 검사하지
  #    않는 이유: 검사가 두 자리에 있으면 한쪽만 고쳐진다.
  #
  # 🔴 리터럴 금지는 그대로다 — AWS 가 발급하는 주소(`*.cloudfront.net` ·
  #    `*.execute-api.*`)를 여기 손으로 박으면 재생성마다 썩는다(TASK-MONO-389 가 고친
  #    결함). `verify-demo-wrapper.sh` (z9)(2) 가 그 명제를 계속 지킨다.
  cors_allowed_origins = distinct(var.allowed_origins)
}

# ---------------------------------------------------------------------------
# EC2 데모 호스트 (평소 stopped, 버튼으로 start/stop)
# ---------------------------------------------------------------------------
resource "aws_security_group" "demo" {
  name_prefix = "${local.name}-"
  description = "on-demand portfolio demo host"
  vpc_id      = local.vpc_id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  # AWS 는 보안그룹 description 에 ASCII 만 허용한다
  # (^[0-9A-Za-z_ .:/()#,@\[\]+=&;{}!$*-]*$). 한글을 넣으면 validate 는 통과하고
  # plan 에서야 거부된다.
  ingress {
    description = "SSH (admin IP only)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_ssh_cidr]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 인스턴스가 SSM 세션매니저로 접속 가능하도록(선택) + CloudWatch
resource "aws_iam_role" "ec2" {
  name_prefix = "${local.name}-ec2-"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# 인스턴스가 도메인 헬스 스냅샷을 발행할 수 있게 한다(TASK-MONO-477). AmazonSSMManagedInstanceCore
# 는 SSM 에이전트용이라 임의 파라미터 PutParameter 를 주지 않는다 — 그 한 파라미터만 최소로 연다.
resource "aws_iam_role_policy" "ec2_health" {
  role = aws_iam_role.ec2.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ssm:PutParameter"]
      Resource = aws_ssm_parameter.health.arn
    }]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name_prefix = "${local.name}-"
  role        = aws_iam_role.ec2.name
}

resource "aws_instance" "demo" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  subnet_id              = local.subnet_id
  vpc_security_group_ids = [aws_security_group.demo.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  # 매 부팅마다 systemd 의 demo-stack.service 가 docker compose up 을 실행한다고 가정.
  # (AMI 에 baked. 첫 부팅 트리거만 user_data 로.)
  user_data = file("${path.module}/../ec2/user-data.sh")

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
  }

  tags = { Name = "${local.name}-host" }

  lifecycle {
    # start/stop 로 상태가 바뀌어도 TF 가 매번 재기동하지 않도록
    ignore_changes = [user_data]
  }
}

# ---------------------------------------------------------------------------
# 상태 저장 (SSM Parameter Store — 무료)
# ---------------------------------------------------------------------------
resource "aws_ssm_parameter" "beat" {
  name  = local.beat_param
  type  = "String"
  value = "0"
  lifecycle { ignore_changes = [value] }
}

resource "aws_ssm_parameter" "started" {
  name  = local.start_param
  type  = "String"
  value = "0"
  lifecycle { ignore_changes = [value] }
}

# 월 누적 가동시간. /start 가 인증 없는 공개 엔드포인트라 idle-stop / max-runtime
# 만으로는 지출 상한이 없다(반복 호출로 둘 다 리셋 가능). 이 값이 실질적 상한이다.
resource "aws_ssm_parameter" "usage" {
  name  = local.usage_param
  type  = "String"
  value = jsonencode({ month = "", seconds = 0, tick = 0 })
  lifecycle { ignore_changes = [value] }
}

# 도메인 헬스 스냅샷(TASK-MONO-477). 인스턴스의 demo-status.timer 가 값을 갱신하므로
# terraform 은 초기값만 심고 이후 드리프트를 무시한다(beat/started/usage 와 동일 패턴).
resource "aws_ssm_parameter" "health" {
  name  = local.health_param
  type  = "String"
  value = "{}"
  lifecycle { ignore_changes = [value] }
}

# ---------------------------------------------------------------------------
# 컨트롤 플레인 Lambda (항상 대기, 과금 거의 0)
# ---------------------------------------------------------------------------
data "archive_file" "lambda" {
  type        = "zip"
  source_dir  = "${path.module}/lambda"
  output_path = "${path.module}/build/handler.zip"
}

resource "aws_iam_role" "lambda" {
  name_prefix = "${local.name}-lambda-"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "lambda" {
  role = aws_iam_role.lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect   = "Allow"
        Action   = ["ec2:StartInstances", "ec2:StopInstances", "ec2:DescribeInstances"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:PutParameter"]
        Resource = [aws_ssm_parameter.beat.arn, aws_ssm_parameter.started.arn, aws_ssm_parameter.usage.arn]
      },
      # /domains 는 헬스 스냅샷을 읽기만 한다(발행은 인스턴스가 한다).
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter"]
        Resource = [aws_ssm_parameter.health.arn]
      },
      # /domain/{start,stop} → 인스턴스에서 demo-up.sh/demo-down.sh <domain> 실행.
      # 리소스를 이 인스턴스 + AWS-RunShellScript 문서로 좁힌다(최소 권한). 리전 자리는
      # "*" — 인스턴스 ID(전역 유일) + 계정으로 이미 좁혀지고, aws_region 데이터소스의
      # .name 이 provider 최신판에서 deprecated 라 불필요한 결합을 피한다.
      {
        Effect = "Allow"
        Action = ["ssm:SendCommand"]
        Resource = [
          "arn:aws:ec2:*:${data.aws_caller_identity.current.account_id}:instance/${aws_instance.demo.id}",
          "arn:aws:ssm:*::document/AWS-RunShellScript",
        ]
      },
      # 명령 실행 결과 조회. 이 액션들은 리소스 수준 권한을 지원하지 않아 "*" 여야 한다.
      {
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation"]
        Resource = "*"
      },
    ]
  })
}

resource "aws_lambda_function" "control" {
  function_name    = "${local.name}-control"
  role             = aws_iam_role.lambda.arn
  runtime          = "python3.12"
  handler          = "handler.handler"
  filename         = data.archive_file.lambda.output_path
  source_code_hash = data.archive_file.lambda.output_base64sha256
  timeout          = 30

  environment {
    variables = {
      INSTANCE_ID            = aws_instance.demo.id
      BEAT_PARAM             = local.beat_param
      STARTED_PARAM          = local.start_param
      USAGE_PARAM            = local.usage_param
      HEALTH_PARAM           = local.health_param
      IDLE_MINUTES           = tostring(var.idle_minutes)
      MAX_RUNTIME_MINUTES    = tostring(var.max_runtime_minutes)
      MONTHLY_BUDGET_MINUTES = tostring(var.monthly_budget_minutes)
      # 🔴 `ALLOWED_ORIGIN` 은 **의도적으로 없다**(TASK-MONO-557). CORS 의 유일한 집은
      # 아래 `cors_configuration` 이다. 예전에는 이 자리가 두 번째 집이었고, 실측 결과
      # 그 두 집은 이미 어긋나 있었다 — 같은 `""` 가 API Gateway 에서는 폴백으로 해소되고
      # Lambda 에서는 `Access-Control-Allow-Origin: ""` 가 됐다. 여기에 다시 넣지 말 것.
    }
  }
}

# ---------------------------------------------------------------------------
# HTTP API (start/stop/status/heartbeat)
# ---------------------------------------------------------------------------
resource "aws_apigatewayv2_api" "api" {
  name          = "${local.name}-api"
  protocol_type = "HTTP"
  cors_configuration {
    # 🔴 **CORS 의 유일한 집이다.** Lambda 는 더 이상 Access-Control-* 를 싣지 않는다
    # (TASK-MONO-557). 두 곳에서 실으면 헤더가 중복되고 브라우저가 거부한다.
    #
    # 사이트 자신의 CloudFront 오리진은 **항상 참조로** 들어간다 — 손으로 박으면
    # 재생성마다 썩는다(결함 2: 커밋된 리터럴). 추가 오리진은 var.allowed_origins.
    #
    # ⚠️ CORS 는 보안 경계가 아니다 — 브라우저 정책일 뿐이고 curl 로 우회된다.
    # `/start` 의 실질적 상한은 여전히 Lambda 의 월 예산 가드뿐이다.
    allow_origins = local.cors_allowed_origins
    allow_methods = ["GET", "POST", "OPTIONS"]
    allow_headers = ["content-type"]
  }
}

resource "aws_apigatewayv2_integration" "lambda" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.control.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "routes" {
  for_each = toset([
    "POST /start", "POST /stop", "GET /status", "POST /heartbeat",
    # 도메인별 선택 (TASK-MONO-477)
    "GET /domains", "POST /domain/start", "POST /domain/stop",
  ])
  api_id    = aws_apigatewayv2_api.api.id
  route_key = each.value
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.api.id
  name        = "$default"
  auto_deploy = true

  # 엔드포인트가 인증 없이 공개된다. 스로틀링은 지출 상한이 아니라(그건 Lambda 의
  # 월 예산 가드가 담당) 무차별 호출로 Lambda/API 요금이 튀는 것을 막는 완충이다.
  default_route_settings {
    throttling_rate_limit  = 5
    throttling_burst_limit = 10
  }
}

resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.control.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.api.execution_arn}/*/*"
}

# ---------------------------------------------------------------------------
# EventBridge — 5분마다 idle-check
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_event_rule" "idle" {
  name                = "${local.name}-idle-check"
  schedule_expression = "rate(5 minutes)"
}

resource "aws_cloudwatch_event_target" "idle" {
  rule      = aws_cloudwatch_event_rule.idle.name
  target_id = "lambda"
  arn       = aws_lambda_function.control.arn
  input     = jsonencode({ action = "idle-check" })
}

resource "aws_lambda_permission" "events" {
  statement_id  = "AllowEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.control.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.idle.arn
}

# ---------------------------------------------------------------------------
# 정적 "Start Demo" 사이트 — **여기서 서빙하지 않는다** (TASK-MONO-579, ADR-MONO-067 D3)
#
# 이 자리에는 S3(비공개) + CloudFront(OAC) 배포가 있었다(TASK-MONO-389). 지웠다.
# 지운 이유는 그것이 틀려서가 아니라 **론처가 두 집을 갖고 있었기 때문**이다:
#
#   Vercel 판(`kanggle-portfolio.vercel.app`)  ← 커밋마다 자동으로 다시 구워진다
#   S3/CloudFront 판                            ← `terraform apply` 때만 갱신된다
#
# 🔴 그 비대칭 때문에 드리프트는 **우연이 아니라 설계상 일어나는 일**이었다. apply 는
#    소유자 승인 대상이라 몇 주씩 안 돌 수 있고, 그동안 두 판은 조용히 갈라진다.
#    그리고 두 사본은 **판정 능력조차 대칭이 아니었다** — S3 판에는 `build-info.json`
#    이 없어서 *"내용이 같은가"* 만 물을 수 있고 *"어느 판인가"* 는 못 물었다.
#
# 🔵 동일성 가드를 새로 만드는 길도 있었고, 실제로 도구는 **이미 있었다**
#    (`site/check-launcher-fresh.sh --origin <URL>`). 그런데 CloudFront 주소는
#    저장소에 없고 **terraform state 안에만** 있다 ⇒ CI 가 그 축을 잴 방법이 원리적으로
#    없다. **집이 하나면 잴 것도 하나다** — 그래서 가드가 아니라 폐기를 골랐다.
#
# 론처의 집은 이제 **Vercel 하나**다(ADR-MONO-067 D3 = Vercel 정본).
# `config.js` 는 `site/build.sh` 가 `DEMO_API_BASE` 환경변수에서 만들고, 그 값이 없으면
# **빌드를 죽인다**(fail-closed). 즉 이 파일이 렌더하던 한 줄의 자리를 그쪽이 이미 갖고 있다.
#
# 🔴 **되돌리려면**: 이 블록을 되살리는 것만으로는 부족하다. `local.cors_allowed_origins`
#    가 다시 CloudFront 를 참조해야 하고, `verify-demo-wrapper.sh` (z9)(2) 의 핀도 함께
#    되돌려야 한다. 세 자리가 한 사실을 나눠 갖고 있다.
# ---------------------------------------------------------------------------
data "aws_caller_identity" "current" {}
