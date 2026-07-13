output "site_url" {
  description = "방문자가 여는 주소 — 'Start Demo' 버튼이 있는 곳. 이것이 데모의 정문이다."
  value       = "https://${aws_cloudfront_distribution.site.domain_name}"
}

output "api_base_url" {
  description = <<-EOT
    제어 API 베이스 URL. **사이트에 손으로 넣을 필요가 없다** — terraform 이
    config.js 를 렌더해 자동 주입한다(TASK-MONO-389). 이 output 은 디버깅용이다.
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
