#!/usr/bin/env bash
# 서버(AWS EC2)에서 최신 main 으로 배포한다 → README 7장.
#   ./scripts/deploy.sh            # git pull + 이미지 다시 빌드 + 재시작 + 응답 확인
#   ./scripts/deploy.sh --no-pull  # 지금 코드 그대로 다시 빌드·재시작
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "[deploy] .env 가 없습니다. cp .env.example .env 후 값을 채우세요 (README 7장)." >&2
  exit 1
fi

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile app"

if [ "${1:-}" != "--no-pull" ]; then
  echo "[deploy] git pull (main)"
  git checkout -q main
  git pull --ff-only
fi
echo "[deploy] 배포할 커밋: $(git log -1 --format='%h %s')"

echo "[deploy] 이미지 빌드 + 재시작 (DB 데이터와 업로드 이미지는 볼륨에 남는다)"
$COMPOSE up -d --build

echo "[deploy] 앱 응답 기다리는 중 (최대 3분)"
for _ in $(seq 1 90); do
  if curl -fs -o /dev/null http://localhost/v3/api-docs; then
    echo "[deploy] 완료: http://<서버 주소>/swagger-ui.html"
    $COMPOSE ps
    exit 0
  fi
  sleep 2
done

echo "[deploy] 3분 안에 응답이 없습니다. 로그를 확인하세요:" >&2
$COMPOSE logs --tail 80 app >&2
exit 1
