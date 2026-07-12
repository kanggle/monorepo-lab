output "api_base_url" {
  description = "정적 사이트가 호출할 API 베이스 URL — site/index.html 의 API_BASE 에 넣으세요"
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
