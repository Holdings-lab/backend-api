# backend-api

backend-api는 Holdings-lab의 백엔드 기능을 통합 관리.

이 저장소에서 실제 메인 서비스는 api-server이며, 나머지 구성은 개발/검증/데모 목적의 보조 리소스.

## 저장소 구성

- 주요 서비스: api-server
	- Spring Boot 기반 메인 API 서버
	- 인증/사용자 선호, 이벤트/홈/인사이트 API, 외부 ML 연동(Webhook/Trigger), Firebase 알림 연동 담당
- 데모/보조 구성: data-ml, pwa-client, postgresql, postman, scripts
	- 기능 검증, 테스트 데이터 준비, 로컬 실험/시연용 구성

## API Server

api-server는 Spring Boot 기반의 메인 API 백엔드.

## 프로젝트 목적(데모)

- 사용자 맞춤 자산/알림 설정을 저장하고 API로 제공.
- 이벤트/콘텐츠 데이터를 조회하는 엔드포인트를 통합 제공.
- ML 서버와 연동해 파이프라인 트리거 및 학습 요청을 백엔드에서 중계.

## 프로젝트 개요(대략적인 API 파이프라인)

1. `controller/`에서 HTTP 요청을 수신.
	 - `AuthController`, `EventController`, `HomeController`, `InsightController` 등 도메인별 진입점 제공.

2. `service/`에서 도메인 로직과 연동 로직 처리.
	 - `auth/`, `event/`, `home/`, `insight/`, `integration/` 하위 서비스로 기능을 분리.

3. `repository/`를 통해 PostgreSQL(JPA) 영속 계층에 접근.
	 - 사용자, 이벤트 알림, 프로필, 워치자산 등 엔티티를 저장/조회.

4. `config/`, `exception/`에서 공통 정책 적용.
	 - 응답 래핑, 비동기 설정, Firebase 초기화, 전역 예외 핸들링 수행.

## 핵심 실행 흐름

1. 클라이언트가 API 요청을 전송.
2. 컨트롤러가 DTO로 요청을 받고 서비스에 위임.
3. 서비스가 도메인 규칙을 적용하고 필요 시 외부 API(ML/Firebase) 호출.
4. 리포지토리가 DB를 조회/저장.
5. 공통 응답 포맷(`ApiResponse`)으로 결과 반환.
6. 예외 발생 시 전역 핸들러(`GlobalExceptionHandler`)에서 일관된 에러 응답 반환.

## 디렉터리 구조

```text
api-server/
├─ gradle/
│  └─ wrapper/
│     └─ gradle-wrapper.properties
├─ src/
│  └─ main/
│     ├─ java/
│     │  └─ com/project/server/
│     │     ├─ ServerApplication.java
│     │     ├─ config/
│     │     │  ├─ ApiSuccessMetaResolver.java
│     │     │  ├─ ApiSuccessResponseAdvice.java
│     │     │  ├─ AsyncConfig.java
│     │     │  ├─ FirebaseConfig.java
│     │     │  └─ LegacyUserEmailMigrationRunner.java
│     │     ├─ controller/
│     │     │  ├─ AiPipelineController.java
│     │     │  ├─ AuthController.java
│     │     │  ├─ ContentFeedController.java
│     │     │  ├─ EventController.java
│     │     │  ├─ HealthController.java
│     │     │  ├─ HomeController.java
│     │     │  ├─ InsightController.java
│     │     │  ├─ UserPreferenceController.java
│     │     │  └─ WebhookController.java
│     │     ├─ domain/
│     │     │  ├─ PolicyEvent.java
│     │     │  ├─ PolicyEventEntity.java
│     │     │  ├─ Subscription.java
│     │     │  ├─ User.java
│     │     │  ├─ UserEntity.java
│     │     │  ├─ UserEventAlertEntity.java
│     │     │  ├─ UserNotificationSettingEntity.java
│     │     │  ├─ UserProfileEntity.java
│     │     │  └─ UserWatchAssetEntity.java
│     │     ├─ dto/
│     │     │  ├─ ActionDto.java
│     │     │  ├─ ApiErrorResponse.java
│     │     │  ├─ ApiResponse.java
│     │     │  ├─ AuthDto.java
│     │     │  ├─ EventDto.java
│     │     │  ├─ HomeDto.java
│     │     │  ├─ InsightDto.java
│     │     │  ├─ PolicyEventResponse.java
│     │     │  ├─ PolicyFeedDto.java
│     │     │  ├─ UserPreferenceDto.java
│     │     │  ├─ WatchAssetDto.java
│     │     │  └─ WebhookRequest.java
│     │     ├─ exception/
│     │     │  ├─ ApiException.java
│     │     │  ├─ ErrorResponseCode.java
│     │     │  └─ GlobalExceptionHandler.java
│     │     ├─ repository/
│     │     │  ├─ PolicyEventJpaRepository.java
│     │     │  ├─ UserEventAlertRepository.java
│     │     │  ├─ UserJpaRepository.java
│     │     │  ├─ UserNotificationSettingRepository.java
│     │     │  ├─ UserProfileRepository.java
│     │     │  └─ UserWatchAssetRepository.java
│     │     └─ service/
│     │        ├─ auth/
│     │        │  ├─ AuthService.java
│     │        │  ├─ NotificationSettingsService.java
│     │        │  ├─ UserPreferenceService.java
│     │        │  └─ WatchAssetSelectionService.java
│     │        ├─ event/
│     │        │  ├─ EventAlertService.java
│     │        │  ├─ EventScheduleService.java
│     │        │  └─ EventService.java
│     │        ├─ home/
│     │        │  ├─ FeaturedEventStateService.java
│     │        │  └─ HomeService.java
│     │        ├─ insight/
│     │        │  └─ InsightService.java
│     │        └─ integration/
│     │           ├─ AiPipelineTriggerService.java
│     │           ├─ NotificationService.java
│     │           ├─ PolicyFeedProxyService.java
│     │           └─ RegressionTrainingService.java
│     └─ resources/
│        ├─ application.yml
│        └─ data.sql
├─ .gitignore
├─ Dockerfile
├─ build.gradle
├─ gradlew
├─ gradlew.bat
└─ settings.gradle
```

## 주요 파일 가이드

### 메인 애플리케이션

- `src/main/java/com/project/server/ServerApplication.java`
	Spring Boot 애플리케이션 진입점.

### 컨트롤러 계층

- `src/main/java/com/project/server/controller/AuthController.java`
	인증/사용자 관련 엔드포인트.
- `src/main/java/com/project/server/controller/EventController.java`
	정책/이벤트 관련 조회 및 처리 엔드포인트.
- `src/main/java/com/project/server/controller/HomeController.java`
	홈 화면용 집계/요약 데이터 엔드포인트.
- `src/main/java/com/project/server/controller/InsightController.java`
	인사이트 데이터 엔드포인트.
- `src/main/java/com/project/server/controller/WebhookController.java`
	외부 시스템(Webhook) 연동 엔드포인트.

### 서비스 계층

- `src/main/java/com/project/server/service/auth/`
	사용자 선호/알림 설정/워치자산 관리 로직.
- `src/main/java/com/project/server/service/event/`
	이벤트 조회/일정/알림 로직.
- `src/main/java/com/project/server/service/home/`
	홈 화면 구성 데이터 조합 로직.
- `src/main/java/com/project/server/service/insight/`
	인사이트 계산/조회 로직.
- `src/main/java/com/project/server/service/integration/`
	ML API, 피드 프록시, 알림 외부 연동 로직.

### 설정/예외/저장소

- `src/main/java/com/project/server/config/`
	비동기 처리, 공통 응답 래핑, Firebase 초기화 등 전역 설정.
- `src/main/java/com/project/server/exception/`
	커스텀 예외와 표준화된 에러 응답 처리.
- `src/main/java/com/project/server/repository/`
	JPA 기반 DB 접근 인터페이스.


## API 서버 접근

배포된 api-server는 AWS Lightsail에서 실행 중.

### API 엔드포인트

기본 주소: `http://<server-address>:8080`

### 헬스 체크

서버 정상 작동 여부 확인.

```bash
curl http://<server-address>:8080/health
```

### 상세 스펙

각 엔드포인트의 요청/응답 스펙은 저장소 내 [postman](postman/) 컬렉션을 참고하거나, Notion의 API LIST를 확인.

---

## 🛠️ Development & Test Tools
백엔드 기능 검증 및 학습을 위한 테스트 환경 구성 요소.

### 1. data-ml
- Holdings-lab의 data-ml 레포지토리.

### 2. pwa-client
- API 연동 검증용 프론트엔드 테스트베드(Vite 기반 PWA).
- 인증/조회/설정 API를 실제 화면 흐름에서 확인할 때 사용.

### 3. postgresql
- 로컬/테스트 DB 구성을 위한 PostgreSQL 설정 모음.

### 4. postman
- API 수동 테스트를 위한 Postman 컬렉션 보관.