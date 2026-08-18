# backend-api

backend-api는 Holdings-lab의 백엔드 API와 ML 연동 구성을 통합 관리.

이 저장소에서 실제 메인 서비스는 api-server이며, data-ml은 크롤링/피처링/예측을 담당하는 연동 엔진이다. 나머지 구성은 개발/검증/데모 목적의 보조 리소스.

## 저장소 구성

- 주요 서비스: api-server
	- Spring Boot 기반 메인 API 서버
	- 인증/사용자 선호, 이벤트/홈/인사이트 API, 브로커 계좌/포트폴리오/신호 API, 관리자용 계정/계좌 관리 API, 외부 ML 연동(Webhook/Trigger), Firebase 알림 연동 담당
	- PostgreSQL(JPA) 기반 사용자/프로필/알림/워치자산/계좌 상태/포트폴리오를 저장하고 조회하는 백엔드 중심 서비스
- 주요 서비스: data-ml
	- Holdings-lab의 data-ml 레포지토리
	- Python 기반 크롤링, 전처리, 피처링, 모델 학습/재학습, 예측 실험을 수행하는 배치/실험 엔진
	- api-server에서 트리거하는 ML 파이프라인과 연동되어 데이터 수집부터 예측 결과 생성까지 이어지는 흐름을 담당
- 데모/보조 구성: postgresql, postman, scripts
	- 기능 검증, 테스트 데이터 준비, 로컬 실험/시연용 구성

## API Server

api-server는 Spring Boot 기반의 메인 API 백엔드.

## 프로젝트 목적(데모)

- 사용자 맞춤 자산/알림 설정을 저장하고 API로 제공.
- 이벤트/콘텐츠 데이터를 조회하는 엔드포인트를 통합 제공.
- data-ml에서 생성된 크롤링/학습 결과를 바탕으로 만들어낸 예측 데이터를 전달.
- 관리자용 계정 추가, 비밀번호 변경, FCM 토큰 갱신, 계좌 상세 데이터 입력 같은 운영성 API도 함께 제공.
- 브로커 계좌와 포트폴리오 데이터를 저장하고 집계해 홈/인사이트/API 응답에 반영.

## 프로젝트 개요(대략적인 API 파이프라인)

1. `controller/`에서 HTTP 요청을 수신.
	 - `AuthController`, `BrokerAccountController`, `PortfolioController`, `SignalController`, `EventController`, `HomeController`, `InsightController` 등 도메인별 진입점 제공.

2. `service/`에서 도메인 로직과 연동 로직 처리.
	 - `admin/`, `auth/`, `broker/`, `event/`, `home/`, `insight/`, `integration/`, `portfolio/`, `security/`, `user/` 하위 서비스로 기능을 분리.

3. `repository/`를 통해 PostgreSQL(JPA) 영속 계층에 접근.
	 - 사용자, 이벤트 알림, 프로필, 워치자산, 브로커 계좌, 포트폴리오, 신호 관련 엔티티를 저장/조회.

4. `config/`, `exception/`에서 공통 정책 적용.
	 - 응답 래핑, 비동기 설정, Firebase 초기화, 전역 예외 핸들링 수행.

5. `data-ml/`에서 크롤링/피처링/예측 파이프라인을 수행.
	 - `crawler/`가 원천 데이터를 수집하고, `training/`이 피처링과 모델 학습/예측 실험을 담당.
	 - 현재는 `data-ml/merged_finbert.csv`에서 원천 데이터 수집

## 인증 / 인가

- 로그인·회원가입·OAuth·refresh: `POST /api/auth/**` (공개). access JWT + opaque refresh 발급.
- 사용자 데이터 API — `Authorization: Bearer <accessToken>` 필수.
  - 계정/설정: `/api/me/**`
  - 도메인: `/api/home`, `/api/events`, `/api/accounts`, `/api/portfolio`, `/api/holdings`, `/api/newsroom`, `/api/onboarding`, `/api/ml/**` 등
  - path의 `{userId}`는 사용하지 않음. 서버가 JWT `sub`에서 userId를 추출(`@CurrentUserId`).
- 관리자 API: `/admin/**` — `X-Admin-Key: <ADMIN_API_KEY>` 필수. path `{userId}`는 운영 대상으로 유지.
- Webhook: `/api/internal/webhooks/**` — 기존 `X-Webhook-Secret` 유지.

환경 변수: `JWT_SECRET`, `ADMIN_API_KEY` (`ADMIN_API_KEY` 미설정 시 `/admin`은 전부 401).

## 핵심 실행 흐름

1. 클라이언트가 API 요청을 전송(보호 API는 Bearer 또는 Admin Key 포함).
2. 필터가 JWT/Admin Key를 검증한 뒤 컨트롤러가 DTO로 요청을 받고 서비스에 위임.
3. 서비스가 도메인 규칙을 적용하고 필요 시 외부 API(Hyphen/ML/Firebase) 호출.
4. 리포지토리가 DB를 조회/저장.
5. 공통 응답 포맷(`ApiResponse`)으로 결과 반환.
6. 예외 발생 시 전역 핸들러(`GlobalExceptionHandler`)에서 일관된 에러 응답 반환.
7. 필요한 경우 api-server가 data-ml의 트리거/예측 흐름을 호출해 크롤링 결과와 모델 결과를 연계.

## 디렉터리 구조

```text
api-server/
├─ src/main/java/com/project/server/
│  ├─ config/
│  ├─ controller/
│  │  ├─ AdminController.java
│  │  ├─ MlPipelineController.java
│  │  ├─ AuthController.java
│  │  ├─ BrokerAccountController.java
│  │  ├─ ContentFeedController.java
│  │  ├─ EventController.java
│  │  ├─ HealthController.java
│  │  ├─ HomeController.java
│  │  ├─ InsightController.java
│  │  ├─ PortfolioController.java
│  │  ├─ SignalController.java
│  │  ├─ UserPreferenceController.java
│  │  └─ WebhookController.java
│  ├─ domain/
│  ├─ dto/
│  ├─ exception/
│  ├─ repository/
│  └─ service/
│     ├─ admin/
│     ├─ auth/
│     ├─ broker/
│     ├─ event/
│     ├─ home/
│     ├─ insight/
│     ├─ integration/
│     ├─ portfolio/
│     ├─ security/
│     └─ user/
├─ src/main/resources/
├─ Dockerfile
├─ build.gradle
├─ gradlew
├─ gradlew.bat
└─ settings.gradle

data-ml/
├─ app.py
├─ scheduler.py
├─ crawler/
│  └─ service.py
├─ training/
│  ├─ service.py
│  └─ train_regression.py
├─ data/
├─ merged_finbert.csv
├─ requirements.txt
└─ README.md
```

## 주요 파일 가이드

### 메인 애플리케이션

- `src/main/java/com/project/server/ServerApplication.java`
	Spring Boot 애플리케이션 진입점.

### 컨트롤러 계층

- `src/main/java/com/project/server/controller/AdminController.java`
	관리자 계정/알림/계좌 모의 데이터 관리 엔드포인트.
- `src/main/java/com/project/server/controller/MlPipelineController.java`
	ML 파이프라인 트리거와 학습 요청 엔드포인트.
- `src/main/java/com/project/server/controller/AuthController.java`
	인증/사용자 관련 엔드포인트.
- `src/main/java/com/project/server/controller/BrokerAccountController.java`
	브로커 계좌 조회 및 동기화 엔드포인트.
- `src/main/java/com/project/server/controller/EventController.java`
	정책/이벤트 관련 조회 및 처리 엔드포인트.
- `src/main/java/com/project/server/controller/HealthController.java`
	헬스체크 엔드포인트.
- `src/main/java/com/project/server/controller/HomeController.java`
	홈 화면용 집계/요약 데이터 엔드포인트.
- `src/main/java/com/project/server/controller/InsightController.java`
	인사이트 데이터 엔드포인트.
- `src/main/java/com/project/server/controller/PortfolioController.java`
	포트폴리오 조회/집계 엔드포인트.
- `src/main/java/com/project/server/controller/SignalController.java`
	시그널/실험 결과 관련 엔드포인트.
- `src/main/java/com/project/server/controller/WebhookController.java`
	외부 시스템(Webhook) 연동 엔드포인트.

### 서비스 계층

- `src/main/java/com/project/server/service/admin/`
	관리자 계정 추가/삭제, 비밀번호 변경, FCM 토큰, 계좌 모의 데이터 설정 로직.
- `src/main/java/com/project/server/service/auth/`
	사용자 선호/알림 설정/워치자산 관리 로직.
- `src/main/java/com/project/server/service/broker/`
	브로커 계좌 동기화, 자산 집계, 계좌 정보 조회 로직.
- `src/main/java/com/project/server/service/event/`
	이벤트 조회/일정/알림 로직.
- `src/main/java/com/project/server/service/home/`
	홈 화면 구성 데이터 조합 로직.
- `src/main/java/com/project/server/service/insight/`
	인사이트 계산/조회 로직.
- `src/main/java/com/project/server/service/integration/`
	CODEF, ML, 피드 프록시, 알림 외부 연동 로직.
- `src/main/java/com/project/server/service/portfolio/`
	포트폴리오 응답 구성 및 집계 로직.
- `src/main/java/com/project/server/service/security/`
	보안/인증 관련 보조 로직.
- `src/main/java/com/project/server/service/user/`
	사용자 관련 보조 로직.

### 설정/예외/저장소

- `src/main/java/com/project/server/config/`
	비동기 처리, 공통 응답 래핑, Firebase 초기화 등 전역 설정.
- `src/main/java/com/project/server/exception/`
	커스텀 예외와 표준화된 에러 응답 처리.
- `src/main/java/com/project/server/repository/`
	JPA 기반 DB 접근 인터페이스.

### data-ml

- `data-ml/app.py`
	크롤링/학습 실행을 총괄하는 진입점.
- `data-ml/crawler/service.py`
	원천 데이터를 수집하고 정제하는 크롤링 서비스.
- `data-ml/training/service.py`
	피처링, 학습 데이터 구성, 예측 실행을 담당하는 서비스.
- `data-ml/training/train_regression.py`
	회귀 모델 학습 실행 스크립트.
- `data-ml/scheduler.py`
	주기 실행 및 배치 작업을 조율하는 스케줄러.


## API 서버 접근

배포된 api-server는 AWS Lightsail에서 실행 중.

### API 엔드포인트

기본 주소: `http://<server-address>:8080`

### 헬스 체크

서버 정상 작동 여부 확인.

```bash
curl http://<server-address>:8080/api/health
```

### 상세 스펙

각 엔드포인트의 요청/응답 스펙은 저장소 내 [postman](postman/) 컬렉션을 참고하거나, Notion의 API LIST를 확인.

---

## 🛠️ Development & Test Tools
백엔드 기능 검증 및 학습을 위한 테스트 환경 구성 요소.

### 1. data-ml
- Holdings-lab의 data-ml 레포지토리.
- api-server의 Trigger API와 연결되어 Python 기반 수집/예측 파이프라인을 담당.

### 2. postgresql
- 로컬/테스트 DB 구성을 위한 PostgreSQL 설정 모음.

### 3. postman
- API 수동 테스트를 위한 Postman 컬렉션 보관.