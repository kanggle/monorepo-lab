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

  vpc_id    = var.vpc_id != "" ? var.vpc_id : data.aws_vpc.default[0].id
  subnet_id = var.subnet_id != "" ? var.subnet_id : data.aws_subnets.default[0].ids[0]
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
      IDLE_MINUTES           = tostring(var.idle_minutes)
      MAX_RUNTIME_MINUTES    = tostring(var.max_runtime_minutes)
      MONTHLY_BUDGET_MINUTES = tostring(var.monthly_budget_minutes)
      ALLOWED_ORIGIN         = var.allowed_origin
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
    # 기본값은 사이트 자신의 오리진 — 배포 시점에야 정해지므로 CloudFront 도메인을
    # **참조**한다. 변수에 손으로 박으면 그건 결함 2(커밋된 리터럴)의 재현이다.
    # var.allowed_origin 을 명시하면 그쪽이 이긴다(로컬에서 파일을 열어보는 경우 등).
    #
    # ⚠️ CORS 는 보안 경계가 아니다 — 브라우저 정책일 뿐이고 curl 로 우회된다.
    # `/start` 의 실질적 상한은 여전히 Lambda 의 월 예산 가드뿐이다.
    allow_origins = [
      var.allowed_origin != "" ? var.allowed_origin : "https://${aws_cloudfront_distribution.site.domain_name}"
    ]
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
  for_each  = toset(["POST /start", "POST /stop", "GET /status", "POST /heartbeat"])
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
# 정적 "Start Demo" 사이트 — S3(비공개) + CloudFront(OAC)  [TASK-MONO-389]
#
# 이것이 없던 동안 데모에는 **정문이 없었다**. 저장소는 방문자가 버튼을 누른다고
# 적었지만 그 버튼은 어디에도 배포되지 않았고, `site/index.html` 의 `API_BASE` 는
# **git 에 커밋된 리터럴**이라 재생성마다 죽었다(실제로 죽어 있었다).
#
# 그래서 여기서 고치는 것은 "값" 이 아니라 **모양** 이다: 페이지는 API URL 을
# 들고 있지 않고, terraform 이 **자기 상태에서** `config.js` 한 줄을 렌더한다.
# ⇒ 배포된 페이지가 자기 API 와 어긋나는 것이 **표현 불가능해진다.**
#
# HTML 자체를 templatefile 로 렌더하지 않는 이유: 이 페이지의 JS 는 템플릿
# 리터럴(`${ip}` `${label}` `${status}`)을 여럿 쓰는데 그건 terraform 의 보간
# 문법과 **같은 글자**다. 하나라도 이스케이프를 빠뜨리면 조용히 깨진다.
# 생성되는 것을 한 줄로 격리하면 그 함정이 아예 사라진다.
# ---------------------------------------------------------------------------
data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "site" {
  bucket        = "${local.name}-site-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "site" {
  bucket                  = aws_s3_bucket.site.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "site" {
  name                              = "${local.name}-site-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "site" {
  enabled             = true
  default_root_object = "index.html"
  comment             = "${local.name} on-demand demo launcher"
  price_class         = "PriceClass_200"

  origin {
    domain_name              = aws_s3_bucket.site.bucket_regional_domain_name
    origin_id                = "s3-site"
    origin_access_control_id = aws_cloudfront_origin_access_control.site.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-site"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]

    # 캐시를 짧게 둔다. `config.js` 는 재생성마다 바뀌고, 캐시된 옛 값이 살아 있으면
    # 결함 2(죽은 API_BASE)가 **캐시 층에서 부활한다**.
    min_ttl     = 0
    default_ttl = 60
    max_ttl     = 300

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

data "aws_iam_policy_document" "site" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.site.arn}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.site.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "site" {
  bucket = aws_s3_bucket.site.id
  policy = data.aws_iam_policy_document.site.json
}

resource "aws_s3_object" "index" {
  bucket       = aws_s3_bucket.site.id
  key          = "index.html"
  source       = "${path.module}/../site/index.html"
  etag         = filemd5("${path.module}/../site/index.html")
  content_type = "text/html; charset=utf-8"
  cache_control = "public, max-age=60"
}

# **이 한 줄이 이 task 의 본체다.** 저장소는 API URL 을 들고 있지 않는다 —
# terraform 이 자기 상태에서 만든다. 재생성으로 API id 가 바뀌면 이 객체도 함께
# 바뀐다. 사람이 고칠 것이 없고, 따라서 고치는 것을 잊을 수도 없다.
resource "aws_s3_object" "config" {
  bucket        = aws_s3_bucket.site.id
  key           = "config.js"
  content       = "window.DEMO_API_BASE = ${jsonencode(aws_apigatewayv2_api.api.api_endpoint)};\n"
  content_type  = "application/javascript; charset=utf-8"
  cache_control = "public, max-age=60"
  etag          = md5(aws_apigatewayv2_api.api.api_endpoint)
}
