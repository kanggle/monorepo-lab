#!/bin/bash
# 매 부팅 시 데모 스택을 올린다.
# 전제: AMI 에 docker + docker compose plugin + 모노레포 체크아웃(/opt/monorepo-lab)
#       + /etc/systemd/system/demo-stack.service 가 이미 baked 되어 있음.
#       (유닛 자체는 저장소가 소유한다 — infra/demo/demo-stack.service, TASK-MONO-366)
#
# user_data 는 "첫 부팅" 에만 실행되므로, 재기동에도 항상 뜨게 하려면
# systemd enable 이 핵심이다(아래). 실제 up 은 systemd 가 담당하고,
# 그쪽이 demo-boot.sh 를 불러 IMDSv2 로 DEMO_DOMAIN 을 파생한다.
#
# ⚠️ 이 파일은 `main.tf` 가 file("${path.module}/../ec2/user-data.sh") 로 읽는다.
# TASK-MONO-366 의 승격에서 **누락됐고**, 그래서 저장소 사본으로는 `terraform validate`
# 조차 통과하지 못했다 — 저장소가 "이 코드로 데모를 재현할 수 있다" 고 주장하는 동안.
# 그 주장은 파일 해시 대조로 "검증" 됐는데, **없는 파일은 해시할 것이 없어 대조에 잡히지
# 않는다.** 승격은 TASK-MONO-389.
set -euo pipefail

systemctl daemon-reload
systemctl enable demo-stack.service
systemctl start demo-stack.service
