# Docker Deployment Guide

## 1) Prepare env file

From `backend-api` directory:

```powershell
# Edit .env.docker directly
notepad .env.docker
```

Then edit `.env.docker` with real credentials/secrets.

LLM-related required values:

- `ANTHROPIC_API_KEY` (홈 브리핑·뉴스룸 번역·LLM 헬스체크)
- `ANTHROPIC_MODEL` (default: `claude-3.5-haiku-20241022`)

## 2) Build and run

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.docker up -d --build
```

## 3) Check health

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.docker ps
curl http://localhost:8080/api/health
curl http://localhost:9000/ml/health
curl http://localhost:9000/ml/llm/health
```

## 4) View logs

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.docker logs -f postgres
docker compose -f docker-compose.prod.yml --env-file .env.docker logs -f api-server
docker compose -f docker-compose.prod.yml --env-file .env.docker logs -f data-ml
```

## 5) Stop

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.docker down
```

## 6) AWS EC2 부팅 시 자동 실행(systemd)

아래 절차를 한 번만 수행하면, EC2 재부팅 후에도 `postgres`, `api-server`, `data-ml`이 자동으로 올라옵니다.

```bash
cd /opt/riseai/apps/backend-api
# .env.docker를 실제 운영값으로 수정
chmod +x scripts/install_systemd_autostart.sh
./scripts/install_systemd_autostart.sh
```

상태 확인:

```bash
sudo systemctl status holdings-lab-compose.service --no-pager
docker compose -f docker-compose.prod.yml --env-file .env.docker ps
```

재부팅 테스트:

```bash
sudo reboot
```

재부팅 후 확인:

```bash
sudo systemctl status holdings-lab-compose.service --no-pager
curl http://localhost:8080/api/health
curl http://localhost:9000/ml/health
```

### 컨테이너 이름 변경(pwa-* → 서비스명) 시

기존 서버에 `pwa-postgres` 등이 남아 있으면 포트 충돌이 날 수 있습니다. 볼륨은 유지한 채 재기동:

```bash
cd /opt/riseai/apps/backend-api
docker rm -f pwa-postgres pwa-api-server pwa-data-ml 2>/dev/null || true
docker compose -f docker-compose.prod.yml --env-file .env.docker up -d --remove-orphans
docker compose -f docker-compose.prod.yml --env-file .env.docker ps
# 확인: postgres / api-server / data-ml
```

참고:
- `.env.docker`는 서버별 운영값으로 관리하세요(버전 관리 제외 권장).
- `WEBHOOK_SECRET`는 `api-server`와 `data-ml`에서 동일해야 합니다.
- AWS 배포(`docker-compose.prod.yml`)에서는 루트 `backend-api/.env.docker`만 사용하며, `api-server/.env`와 `data-ml/.env`는 사용하지 않습니다.
- 외부 LLM은 Claude 사용합니다. 뉴스 본문 요약은 크롤 구간에만 로컬 Ollama를 켭니다.
- 컨테이너 간 통신은 `container_name`이 아니라 Compose 서비스명(`postgres`, `api-server`, `data-ml`)을 사용합니다.

## Scheduler behavior (data-ml)

- `SCHEDULE_MODE=interval|hourly|daily|off`
- `INTERVAL_MINUTES` when `interval`
- `DAILY_HOUR`, `DAILY_MINUTE` when `daily`
- `BIS_MAX_PAGES` controls crawl depth
- `CRAWL_SLEEP_SEC` controls request pacing

The scheduled pipeline runs in this order:

1. Crawl (`run_crawl_now`) → CSV append + DB sync
2. Daily sector summaries (`generate_and_store_daily_summaries`)
3. Predict: QQQ/XLE/XLF `predict_signal` (`run_signals_for_tickers`; not `train_regression`)
4. Per-ticker AI briefings
5. Send signal webhook to api-server (QQQ signal)

### Crawler CSV

- `/opt/riseai/data/crawler/policy_updates_features.csv` (via env `POLICY_FEATURES_CSV`)
  - crawl output, prediction window, data-ml DB sync / rebuild news input
- PostgreSQL `policy_documents` (+ features): policy-feed and stored briefings

## LSTM/XGB signal (ml-worker)

data-ml runs `/opt/riseai/apps/ml-worker/shared/predict_signal.py` using
existing feature CSVs (no crawl by default). Default tickers: QQQ, XLE, XLF.

Inside Docker, predict uses the **data-ml container Python** (pandas/torch already
installed). Host `ml-worker/.venv` is not used from inside the container.

Per ticker:

- news: `/opt/riseai/data/features/{qqq|xle|xlf}/news_event_features.csv`
- market: `/opt/riseai/data/features/{qqq|xle|xlf}/market_long_features.csv`
- output: `/opt/riseai/data/predictions/{qqq|xle|xlf}_latest_signal.json`

Crawl/feature refresh is optional (`refreshFeatures: true` on `/ml/signal/run`).

Smoke test on the server:

```bash
# data-ml (existing feature CSVs → QQQ/XLE/XLF)
curl -sS -X POST http://localhost:9000/ml/signal/run \
  -H 'Content-Type: application/json' \
  -d '{}'

# latest file only
curl -sS 'http://localhost:9000/ml/signal/latest?ticker=QQQ'

# api-server proxy
curl -sS -X POST 'http://localhost:8080/api/ml/signal?ticker=QQQ'
```

Expected: JSON with `event_score` / `direction` / `features.source=existing`,
and file at `/opt/riseai/data/predictions/qqq_latest_signal.json`.
