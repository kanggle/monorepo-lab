#!/usr/bin/env bash
# Claude Code PostToolUse 훅 헬퍼 — Docker 이미지 재빌드 직후 dangling 이미지 자동 삭제.
#
# .claude/settings.json 의 PostToolUse(matcher: Bash) 에서 호출된다. stdin 으로 받은
# 훅 JSON(tool_input.command 포함)에서 docker 빌드 여부를 보고, 빌드였으면
# `docker image prune -f` 로 옛(<none>) 이미지를 정리한다.
#
# 설계 원칙:
#   - best-effort: docker 가 죽었거나 prune 이 실패해도 **항상 exit 0** (원래 Bash 툴을
#     절대 실패시키지 않음).
#   - 단순 재시작(`up -d` without --build)엔 미발동(빌드 패턴만 매칭).
#   - prune/cleanup 명령 자체엔 재귀 안 함(image prune / builder prune / docker-cleanup 제외).
#   - 호스트 vhdx 증식 억제용. 실제 C: 회수는 별건 compact-rd-vhdx.ps1(관리자).
#     (프로젝트 메모리 env_rancher_desktop_vhdx_no_shrink / feedback_prune_old_image_after_rebuild)

input="$(cat 2>/dev/null)"

# raw JSON 에서 docker 빌드 신호 탐지 (command 추출 대신 패턴 매칭 — 따옴표/이스케이프에 견고).
is_build() {
  printf '%s' "$input" | grep -Eqi 'docker[^"]*build|[[:space:]]--build([[:space:]"]|$)'
}
is_cleanup() {
  printf '%s' "$input" | grep -Eqi 'image[[:space:]]+prune|builder[[:space:]]+prune|docker-cleanup'
}

if is_build && ! is_cleanup; then
  before="$(docker image prune -f 2>/dev/null | tail -1)"
  # 출력은 훅 로그로만(사용자 화면 비침범). 항상 성공 처리.
  printf 'hook: pruned dangling images after build (%s)\n' "${before:-ok}" >&2 || true
fi

exit 0
