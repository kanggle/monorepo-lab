# ---------------------------------------------------------------------------
# 🔴 `site_url` 은 없다 (TASK-MONO-579, ADR-MONO-067 D3)
#
# 이 자리에 CloudFront 도메인을 내는 output 이 있었다. 그 배포를 폐기하면서 같이 지웠다.
#
# 🔴 **Vercel 주소를 여기 박아서 살리지 않는다.** terraform 이 소유하지 않는 값을
#    terraform 출력으로 두면 **거짓 출처**가 된다 — 나중에 그 주소가 바뀌면 `terraform
#    output` 은 아무 일도 없다는 듯 옛 값을 계속 낸다. 그것이 TASK-MONO-389 가 고친
#    결함(리터럴로 박힌 주소)과 **같은 모양**이다.
#
# 론처가 어디 사는지는 `infra/demo/aws/README.md` 가 말한다 (D3 정본 = Vercel).
# ---------------------------------------------------------------------------

output "api_base_url" {
  description = <<-EOT
    제어 API 베이스 URL.

    🔴 TASK-MONO-579 이후 이 값은 **디버깅용이 아니라 배선용**이다. 예전에는 terraform 이
    S3 사본의 config.js 를 렌더해 자동 주입했으므로 사람이 볼 일이 없었다. 그 사본이
    폐기된 지금, 론처의 유일한 집인 Vercel 은 이 값을 **`DEMO_API_BASE` 환경변수**로 받는다
    (site/build.sh 가 그것으로 config.js 를 만들고, 없으면 빌드를 죽인다).

    ⇒ 이 output 이 바뀌면(API 재생성) **Vercel 프로젝트의 DEMO_API_BASE 도 함께 고쳐야 한다.**
    그 두 자리는 아직 자동으로 묶여 있지 않다.
  EOT
  value       = aws_apigatewayv2_api.api.api_endpoint
}

output "instance_id" {
  value = aws_instance.demo.id
}

output "idle_stop_minutes" {
  value = var.idle_minutes
}

output "max_runtime_minutes" {
  value = var.max_runtime_minutes
}
