// =============================================================================
// demo-ami.pkr.hcl — 온디맨드 데모 호스트 AMI (infra/demo 래퍼 기반)
// =============================================================================
// 데모 스택의 모든 서비스 이미지를 빌드해 AMI 에 구운다.
// → 데모 부팅 시 빌드 0, 콜드스타트 단축.
//
//   packer init . && packer build -var "repo_ref=main" demo-ami.pkr.hcl
//
// -----------------------------------------------------------------------------
// 선행조건 (TASK-MONO-342/344/346 에서 실측으로 확정 — 이전 판은 전부 빠뜨렸다)
// -----------------------------------------------------------------------------
// 1) JDK 21 이 필요하다.
//    모든 Java 서비스 Dockerfile 은 `COPY build/libs/<svc>.jar` 다 — 도커 안에서
//    컴파일하지 않는다. 따라서 호스트에서 gradle 로 jar 를 먼저 만들어야 하고,
//    settings.gradle 에 foojay 리졸버가 없어 gradle 이 toolchain 을 자동
//    다운로드하지 못한다. gradle.properties: javaVersion=21.
//
// 2) 공유 런타임 베이스 이미지 monorepo/java-service-base:v1 이 필요하다
//    (ADR-MONO-041 D2). 레지스트리가 없으므로 로컬에서 먼저 굽는다.
//    CI 의 _platform-e2e.yml 도 정확히 같은 일을 한다.
//
// 3) 이미지 굽기는 `up -d --build` 가 아니라 `build` 여야 한다.
//    이전 판은 `DEMO_BUILD=1 demo-up.sh full` 로 43개 컨테이너를 실제 기동한 뒤
//    down 했다. 빌드 인스턴스에서 40개 JVM 을 띄울 이유가 없고(RAM 낭비 + OOM
//    위험), AMI 에 필요한 것은 이미지 레이어뿐이다.
//
// 4) 프로젝트 .env 는 gitignored 라 fresh clone 에 없다. demo.env 가 값을
//    제공한다(MONO-346). 여기서 별도 seeding 을 하지 않는 이유다.
//
// 빌드는 CPU-바운드(c6i.4xlarge), 런타임은 RAM-바운드(m6i.2xlarge / 32GB).
// =============================================================================

packer {
  required_plugins {
    amazon = {
      version = ">= 1.3"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

// HCL 은 한 줄 블록에 인자를 딱 하나만 허용한다. `{ type = ... default = ... }` 는
// "Invalid single-argument block definition" 으로 파일 전체 파싱이 실패한다.
variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "build_instance" {
  type    = string
  default = "c6i.4xlarge"
}

variable "repo_url" {
  type    = string
  default = "https://github.com/kanggle/monorepo-lab.git"
}

variable "repo_ref" {
  type    = string
  default = "main"
}

// 이 값이 AMI 스냅샷 크기를 정하고, Terraform 의 인스턴스 루트는 그 아래로 못 간다.
// 즉 여기서 고른 숫자가 "인스턴스가 꺼져 있어도 매달 나가는 비용"을 고정한다.
// 실측 추정: OS ~3GB + 저장소/gradle 캐시 ~5GB + 이미지 ~25GB ≈ 30GB.
variable "volume_gb" {
  type    = number
  default = 100
}

// full = 8 프로젝트 전부. demo-core = iam+ecommerce+wms+console.
// AMI 는 런타임 프로파일과 무관하게 full 을 구워두는 편이 낫다(둘 다 커버).
variable "demo_profile" {
  type    = string
  default = "full"
}

source "amazon-ebs" "demo" {
  region        = var.region
  instance_type = var.build_instance
  ssh_username  = "ubuntu"
  // gradle 42 bootJar + 40여 이미지 빌드 — SSH 유휴 타임아웃 여유
  ssh_timeout = "20m"

  // AMI 등록 대기. 기본 waiter 는 10분(40회 × 15초)인데, 100GB 스냅샷은 그보다
  // 오래 걸린다 — 실측에서 프로비저닝을 전부 통과하고도
  // "exceeded max wait time for ImageAvailable waiter" 로 실패했다.
  // (AMI 자체는 그 뒤에 정상 완성됐다. 즉 빌드는 성공인데 packer 만 포기한 상태였다.)
  aws_polling {
    delay_seconds = 30
    max_attempts  = 120 // 60분
  }

  source_ami_filter {
    filters = {
      name                = "ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"
      virtualization-type = "hvm"
      root-device-type    = "ebs"
    }
    owners      = ["099720109477"] // Canonical
    most_recent = true
  }

  launch_block_device_mappings {
    device_name           = "/dev/sda1"
    volume_size           = var.volume_gb
    volume_type           = "gp3"
    delete_on_termination = true
  }

  ami_name        = "portfolio-demo-{{timestamp}}"
  ami_description = "Portfolio on-demand demo host — docker + prebuilt images + demo-stack.service"
  tags = {
    Name    = "portfolio-demo"
    Project = "monorepo-lab"
  }
}

build {
  sources = ["source.amazon-ebs.demo"]

  // ---------------------------------------------------------------------------
  // 1) Docker + compose plugin + JDK 21
  // ---------------------------------------------------------------------------
  provisioner "shell" {
    inline = [
      "set -eux",
      "sudo apt-get update -y",
      "sudo apt-get install -y ca-certificates curl git",
      "sudo install -m 0755 -d /etc/apt/keyrings",
      "sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc",
      "sudo chmod a+r /etc/apt/keyrings/docker.asc",
      "echo \"deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable\" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null",
      "sudo apt-get update -y",
      "sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin",
      // gradle 이 toolchain 을 자동 다운로드하지 않는다(foojay 리졸버 부재).
      // noble 의 openjdk-21 은 Temurin 21 과 동일한 OpenJDK 21 이다.
      "sudo apt-get install -y openjdk-21-jdk-headless",
      "java -version",
      "sudo usermod -aG docker ubuntu",
      "sudo systemctl enable --now docker",
    ]
  }

  // ---------------------------------------------------------------------------
  // 2) 모노레포 체크아웃
  // ---------------------------------------------------------------------------
  provisioner "shell" {
    inline = [
      "set -eux",
      "sudo mkdir -p /opt",
      "sudo git clone --depth 1 --branch ${var.repo_ref} ${var.repo_url} /opt/monorepo-lab",
      "sudo chown -R ubuntu:ubuntu /opt/monorepo-lab",
    ]
  }

  // ---------------------------------------------------------------------------
  // 3) boot jar 42개 — 이미지 빌드의 선행조건
  // ---------------------------------------------------------------------------
  // 루트 `./gradlew bootJar` 는 태스크 이름 매칭으로 bootJar 를 가진 모든
  // 서브프로젝트에서 실행된다(실측: 42개 — ecommerce 12 / wms 7 / iam 7 /
  // fan 5 / scm 4 / erp 4 / finance 2 / console-bff 1).
  //
  // 손으로 나열하지 않는 것이 핵심이다. 서비스가 추가될 때마다 드리프트하는
  // 목록은 이 저장소가 이미 두 번 데인 실패 모드다(MONO-339 의 README 서비스
  // 목록, MONO-344 의 compose 파일 목록).
  //
  // iam 의 community-service / membership-service 는 bootJar 는 있으나
  // Dockerfile 이 없다 — 여분 jar 2개는 무해하다.
  //
  // 사후 가드는 gradle 자신에게 기대값을 묻는다. 매직 넘버(`-ge 40`)를 박으면
  // 서비스가 늘어도 통과하고, `find . -path '*/build/libs/*.jar'` 처럼 넓게 세면
  // libs/ 의 라이브러리 jar 3개까지 세어 **boot jar 0개여도 통과**한다.
  // 세는 범위는 projects/*/apps/*/ 로 한정한다.
  provisioner "shell" {
    inline_shebang = "/bin/bash -e"
    inline = [
      "set -eux",
      "cd /opt/monorepo-lab",
      "./gradlew bootJar --no-daemon",
      "expected=$(./gradlew bootJar --dry-run --no-daemon -q | grep -c ':bootJar SKIPPED$')",
      "actual=$(find projects -path '*/apps/*/build/libs/*.jar' -not -name '*-plain.jar' | wc -l)",
      "echo \"boot jars: expected=$expected actual=$actual\"",
      "test \"$actual\" -ge \"$expected\"",
    ]
    timeout = "60m"
  }

  // ---------------------------------------------------------------------------
  // 4) 공유 런타임 베이스 이미지 (ADR-MONO-041 D2)
  // ---------------------------------------------------------------------------
  // 모든 Java 서비스 Dockerfile 이 `FROM monorepo/java-service-base:v1` 이다.
  // 레지스트리가 없으므로 로컬에 먼저 존재해야 한다.
  //
  // ---------------------------------------------------------------------------
  // docker 를 쓰는 provisioner 는 스크립트 **전체를 root 로** 실행한다(ROOT_EXEC).
  // ---------------------------------------------------------------------------
  // 실측으로 확인된 두 가지 때문이다:
  //
  //  (1) `usermod -aG docker ubuntu` 는 이 빌드에 소용이 없다. Packer 는
  //      provisioner 마다 새로 로그인하지 않고 **SSH 커넥션 하나를 재사용**한다.
  //      보조 그룹은 로그인 시점에 고정되므로 ubuntu 는 끝까지 docker 그룹이
  //      아니다 → `docker info` 가 permission denied 로 죽는다. (이전 판의 주석은
  //      "다음 provisioner 의 새 세션부터 적용된다"고 적었는데, 틀렸다.)
  //
  //  (2) 그렇다고 명령마다 `sudo docker …` 를 붙일 수는 없다. 5단계는
  //      `set -a; source demo.env; set +a` 로 변수를 export 한 뒤 compose 가 그것을
  //      보간해야 하는데, `sudo` 의 env_reset 이 그 변수들을 조용히 비운다 —
  //      MONO-346 이 잡아낸 바로 그 실패 모드(빈 POSTGRES_PASSWORD)를 재현한다.
  //
  // 스크립트를 통째로 root 로 돌리면 source 도 compose 도 같은 셸 안에서 끝나므로
  // env 가 경계를 넘지 않는다. 이미지는 데몬 전역이라 런타임(systemd=root)과도 일치.
  provisioner "shell" {
    execute_command = "chmod +x {{ .Path }}; sudo -H bash {{ .Path }}"
    inline_shebang  = "/bin/bash -e"
    inline = [
      "set -eux",
      "cd /opt/monorepo-lab",
      "docker info >/dev/null",
      "docker build -t monorepo/java-service-base:v1 docker/java-service-base",
      "docker image inspect monorepo/java-service-base:v1 >/dev/null",
    ]
    timeout = "15m"
  }

  // ---------------------------------------------------------------------------
  // 5) 서비스 이미지 빌드 (기동하지 않는다)
  // ---------------------------------------------------------------------------
  // projects.sh 를 source 해 프로젝트 맵을 재사용한다 — 여기에 목록을 복제하면
  // 그 순간 드리프트가 시작된다. demo.env 는 compose 보간에 필요하다(MONO-346).
  //
  // HCL 주의: `${` 는 Packer 의 보간 문법이다. bash 배열 확장은 `$${...}` 로
  // 이스케이프해야 리터럴 `${...}` 가 스크립트에 들어간다.
  //
  // ROOT_EXEC: 4단계 주석 참조. 특히 이 단계가 `sudo docker compose` 를 쓰면 안 되는
  // 이유의 핵심이다 — 아래 `set -a; source demo.env` 로 export 한 값이 sudo 의
  // env_reset 에 지워져 compose 가 빈 문자열을 보간하게 된다.
  // 진단 trap: 1차 시도(attempt 2)는 scm 빌드 도중 **에러 메시지 한 줄 없이**
  // exit 123 으로 죽었고, 로그 마지막 줄이 sha256 해시 중간에서 잘려 있었다
  // (= 출력 스트림이 쓰는 도중 끊김 = 프로세스 하드킬). 원인을 추측하지 않고
  // 죽는 순간의 상태를 로그에 남긴다: OOM 이면 dmesg 에, 디스크면 df 에 찍힌다.
  //
  // 프로젝트마다 df/free 를 찍어 추세를 본다 — 마지막 스냅샷만으로는 "터지기
  // 직전까지 여유로웠다" 와 "서서히 차올랐다" 를 구분할 수 없다.
  provisioner "shell" {
    execute_command = "chmod +x {{ .Path }}; sudo -H bash {{ .Path }}"
    inline_shebang  = "/bin/bash -e"
    inline = [
      "set -eux",
      "trap 'rc=$?; set +x; echo \"===== DIED rc=$rc =====\"; df -h /; free -m; echo \"--- dmesg tail ---\"; dmesg | tail -40; echo \"--- docker ---\"; docker system df || true; exit $rc' EXIT",
      "cd /opt/monorepo-lab",
      "export ROOT=/opt/monorepo-lab",
      "source infra/demo/projects.sh",
      "set -a; source infra/demo/demo.env; set +a",
      "case '${var.demo_profile}' in full) SET=(\"$${FULL[@]}\");; demo-core) SET=(\"$${CORE[@]}\");; *) echo 'bad demo_profile' >&2; exit 2;; esac",
      // 빌드 출력은 **인스턴스의 파일로** 보낸다 — SSH 로 스트리밍하지 않는다.
      //
      // 이유(실측): attempt 2 는 scm 이미지 export 중, attempt 3 은 console-web 의
      // Next.js 라우트 테이블 출력 중에 죽었다. **서로 다른 지점** = 결정론적 단계
      // 실패가 아니다. 두 번 다 (a) 출력이 폭주하던 순간이고 (b) 로그 마지막 줄이
      // 문자 중간에서 잘렸으며 (c) EXIT trap 이 아예 실행되지 않았다(= bash 하드킬).
      // 메모리는 30GB 여유, 디스크는 64GB 여유였으므로 OOM·디스크가 아니다.
      // 남는 설명은 대용량 출력 중 SSH 채널이 끊긴 것.
      //
      // 실패 시에만 tail 을 찍어 원인 파악에 필요한 만큼만 흘린다.
      // build 는 `build:` 를 가진 서비스(우리가 소스에서 굽는 앱)만 만든다. postgres·
      // kafka·redis·grafana 같은 **서드파티 image: 서비스는 AMI 에 들어가지 않아**
      // 매 부팅마다 레지스트리에서 pull 된다. 그래서 pull 을 함께 굽는다:
      //
      //   1) 콜드스타트가 짧아진다 — 이미지를 미리 굽는 목적의 나머지 절반이다.
      //   2) **죽은 이미지를 부팅이 아니라 AMI 빌드에서 잡는다.** 실제로 이 누락 때문에
      //      `bitnami/kafka:3.7` 삭제(Docker Hub 에서 사라짐)를 AMI 가 조용히 통과했고,
      //      EC2 를 실제로 부팅해서야 스택이 33/43 에서 멈추는 것으로 드러났다
      //      (TASK-MONO-353). pull 이 있었다면 빌드가 그 자리에서 실패했다.
      "for p in \"$${SET[@]}\"; do mapfile -t ARGS < <(compose_args \"$p\"); echo \"[ami] build: $p  ($(df -h / | awk 'NR==2{print $4\" free\"}') / $(free -m | awk 'NR==2{print $7\"MB avail\"}'))\"; if ! docker compose -p \"$p\" \"$${ARGS[@]}\" build >\"/var/log/ami-build-$p.log\" 2>&1; then echo \"!!! build FAILED: $p\"; tail -80 \"/var/log/ami-build-$p.log\"; exit 1; fi; if ! docker compose -p \"$p\" \"$${ARGS[@]}\" pull --ignore-buildable >>\"/var/log/ami-build-$p.log\" 2>&1; then echo \"!!! pull FAILED: $p (레지스트리에서 사라진 이미지?)\"; tail -40 \"/var/log/ami-build-$p.log\"; exit 1; fi; echo \"[ami]   ok: $p\"; done",
      "docker image prune -f",    // dangling 만 (태그된 이미지 보존)
      "docker builder prune -af", // 빌드캐시는 AMI 에 불필요
      "docker image ls",
      "df -h /; free -m",
    ]
    timeout = "90m"
  }

  // ---------------------------------------------------------------------------
  // 6) 부팅 시 자동 기동할 systemd 유닛 — **저장소에서** 설치한다
  // ---------------------------------------------------------------------------
  // 예전에는 이 유닛을 Packer 옆의 사본(`../ec2/demo-stack.service`)에서 업로드했다.
  // 그래서 **저장소가 부팅 계약을 바꿔도 유닛은 몰랐다** — TASK-MONO-358 이 "데모는
  // `DEMO_DOMAIN` 을 받아야 한다" 는 계약을 만들었는데 유닛은 여전히 `demo-up.sh` 를
  // 직접 불렀고, 스택은 96개 컨테이너가 전부 healthy 한 채로 `*.local` 에 떠서
  // **아무도 도달할 수 없었다**(TASK-MONO-366).
  //
  // 인스턴스에는 이미 저장소가 클론돼 있다(단계 2). 사본을 만들 이유가 없다.
  // 가드 (n) 이 이 경로를 지킨다 — 저장소 밖에서 유닛을 가져오면 FAIL.
  provisioner "shell" {
    inline = [
      "set -eux",
      "sudo install -m 0644 /opt/monorepo-lab/infra/demo/demo-stack.service /etc/systemd/system/demo-stack.service",
      "sudo systemctl daemon-reload",
      "sudo systemctl enable demo-stack.service",
      // 유닛이 실제로 부팅 진입점을 부르는지 AMI 안에서 확인한다. 여기서 틀리면
      // 부팅 후에야(=방문자가 404 를 보고서야) 알게 된다.
      "grep -q 'demo-boot.sh' /etc/systemd/system/demo-stack.service",
      "test -x /opt/monorepo-lab/infra/demo/demo-boot.sh || sudo chmod +x /opt/monorepo-lab/infra/demo/demo-boot.sh",
      // AMI 굽는 시점에는 start 하지 않는다 (부팅 시 기동)
    ]
  }

  // ---------------------------------------------------------------------------
  // 7) 정적 가드 — AMI 가 부팅 가능한 상태인지 (기동 없이 확인 가능한 범위)
  // ---------------------------------------------------------------------------
  // verify-demo-wrapper.sh 정적 (a)~(e),(g). (g)가 특히 중요하다: 이 AMI 는
  // fresh clone 이므로 프로젝트 .env 가 없고, demo.env 가 값을 전부 대야 한다.
  //
  // ROOT_EXEC: 래퍼가 `docker compose config` 를 돌린다 → 데몬 접근 필요(4단계 주석).
  provisioner "shell" {
    execute_command = "chmod +x {{ .Path }}; sudo -H bash {{ .Path }}"
    inline_shebang  = "/bin/bash -e"
    inline = [
      "set -eux",
      "cd /opt/monorepo-lab",
      "bash infra/demo/verify-demo-wrapper.sh",
    ]
    timeout = "10m"
  }

  // ---------------------------------------------------------------------------
  // 실패 시 진단 — 인스턴스가 파괴되기 전에 마지막으로 돌아간다
  // ---------------------------------------------------------------------------
  // 인라인 EXIT trap 은 bash 가 하드킬되면 실행되지 않는다(attempt 3 에서 실증).
  // error-cleanup-provisioner 는 **packer 가** 별도 세션으로 돌리므로, 원격 스크립트가
  // 어떻게 죽었든 실행된다. OOM 이면 dmesg/journal 에, 디스크면 df 에 남는다.
  error-cleanup-provisioner "shell" {
    execute_command = "chmod +x {{ .Path }}; sudo -H bash {{ .Path }}"
    inline = [
      "echo '===== POST-FAILURE DIAGNOSTICS ====='",
      "uptime; free -m; df -h /",
      "echo '--- kernel: OOM / kill 흔적 ---'",
      "(dmesg | grep -iE 'out of memory|oom|killed process' | tail -20) || echo '(none)'",
      "echo '--- docker daemon 살아있나 ---'",
      "(systemctl is-active docker) || true",
      "(docker info >/dev/null 2>&1 && echo 'daemon: reachable') || echo 'daemon: UNREACHABLE'",
      "echo '--- 각 프로젝트 빌드 로그 tail ---'",
      "for f in /var/log/ami-build-*.log; do echo \"### $f\"; tail -5 \"$f\"; done 2>/dev/null || echo '(no build logs)'",
      "echo '--- 마지막 커널 메시지 ---'",
      "dmesg | tail -25",
    ]
  }
}
