#!/bin/bash
# auth-service를 .env.local 환경변수와 함께 실행
set -a
source "$(dirname "$0")/.env.local" 2>/dev/null
set +a

java -jar "$(dirname "$0")/build/libs/auth-service.jar" --spring.profiles.active=standalone "$@"
