# 이미지 처리 서버

클라이언트의 이미지 처리 요청을 수신하여 외부 AI 처리 서비스(Mock Worker)에 위임하고,
작업 상태와 결과를 추적할 수 있도록 하는 비동기 작업 관리 서버입니다.

---

## 기술 스택

- Java 17 / Spring Boot 3.5
- Spring Data JPA / PostgreSQL
- Spring WebFlux (WebClient)
- Docker / Docker Compose
- JUnit 5 / Mockito / MockWebServer

---

## 실행 방법


### Docker로 실행

```bash
# 1. JAR 빌드
./gradlew bootJar -x test

# 2. 컨테이너 실행
docker compose up -d
```

서버: `http://localhost:8080`
PostgreSQL: `localhost:5432`

---

### 로컬에서 직접 실행

위 docker compose로 DB를 먼저 띄운 후 실행합니다.

```bash
# DB만 실행
docker compose up db -d

# 앱 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

---

## API 명세


Swagger UI: http://localhost:8080/swagger-ui/index.html

### POST /api/jobs — 이미지 처리 요청

```
Headers:
  Idempotency-Key: {UUID}   # 중복 요청 방지용

Body:
  { "imageUrl": "https://..." }
```

### GET /api/jobs/{jobId} — 작업 조회


### GET /api/jobs — 전체 작업 목록

---

## 시스템 구조

### 전체 흐름

```
Client → [내 서버] → Mock Worker (외부 AI 서비스)
```

Mock Worker는 GPU 기반의 무거운 AI 추론 작업을 수행하며, 응답 시간이 수 초에서 수십 초까지 변동됩니다. 이를 동기적으로 처리하면 클라이언트는 응답을 받기까지 오랜 시간 대기해야 합니다.

이 문제를 해결하기 위해 **DB 폴링 기반 비동기 처리 패턴**을 채택했습니다.

1. 클라이언트가 요청을 보내면 서버는 즉시 `jobId`를 반환합니다
2. 백그라운드에서 서버가 Mock Worker에 작업을 제출하고 완료를 기다립니다
3. 클라이언트는 `jobId`로 폴링하여 작업 상태와 결과를 확인합니다

### 컴포넌트 구성

**JobController** — 클라이언트 요청을 수신하여 JobService를 호출하고 응답을 반환합니다.

**JobService** — Job 생성 및 조회 비즈니스 로직을 담당합니다. Idempotency-Key 기반 중복 처리를 포함합니다.

**JobRepository (DB)** — Job 상태를 영속화합니다. `idempotency_key`에 unique 설정, `status`에 index를 적용합니다.

**JobScheduler** — 두 개의 스케줄러가 주기적으로 DB를 확인하여 상태를 전이합니다.
- 제출: `PENDING` Job을 감지하여 Mock Worker에 제출하고 `PROCESSING`으로 전환합니다
- 상태 확인: `PROCESSING` Job을 감지하여 Mock Worker에 상태를 조회하고 `COMPLETED` 또는 `FAILED`로 전환합니다

**MockWorkerClient** — WebClient 기반 HTTP 클라이언트입니다. 일시적 오류(429/5xx)에 대해 Exponential Backoff로 최대 3회 재시도합니다.

### 핵심 설계 결정

**요청 접수와 처리 위임의 분리**: 클라이언트 요청이 도달한 순간 즉시 DB에 `PENDING`으로 저장합니다. Mock Worker 호출 성공 여부와 무관하게 요청 사실을 먼저 기록함으로써, 외부 시스템 장애 시에도 요청이 유실되지 않습니다.

**스케줄러 기반 상태 관리**: 별도의 스케줄러가 DB를 주기적으로 확인하여 `PENDING` → `PROCESSING` → `COMPLETED/FAILED` 전이를 책임집니다. 서버가 재시작되더라도 DB에 남아있는 Job 상태를 기반으로 자연스럽게 복구됩니다.

**폴링 방식**: Mock Worker가 Webhook을 지원하지 않으므로 완료 감지는 서버 내부 폴링으로 처리합니다. 동시에 여러 Job을 비동기로 폴링하면 Mock Worker에 429를 유발할 수 있어, 순차적으로 처리합니다.



---

## 설계 설명

### 상태 모델 설계 의도

```
PENDING → PROCESSING → COMPLETED
          ↘        ↙
            FAILED
```

| 상태 | 의미 | 전이 가능 상태 |
|------|------|--------------|
| `PENDING` | 클라이언트 요청 수신 완료, Mock Worker 미제출 | `PROCESSING`, `FAILED` |
| `PROCESSING` | Mock Worker에 제출 완료, 결과 대기 중 | `COMPLETED`, `FAILED` |
| `COMPLETED` | 처리 완료, 결과 존재 | (종료) |
| `FAILED` | 처리 실패, 에러 메시지 존재 | (종료) |

허용되지 않는 전이(`PROCESSING → PENDING` 등)는 도메인 레이어에서 예외를 발생시켜 차단합니다.

**PENDING을 분리한 이유**: 클라이언트 요청이 서버에 도달한 사실을 외부 시스템 호출 성공 여부와 무관하게 즉시 DB에 기록합니다. "요청의 접수"와 "처리의 위임"을 분리함으로써, Mock Worker 장애가 발생해도 요청이 유실되지 않고 재시도 가능한 상태로 남습니다.

**종료 상태**: `COMPLETED`와 `FAILED`는 단방향 종료 상태입니다. 어떤 상태로도 전이할 수 없으며, 재처리가 필요한 경우 새로운 Idempotency-Key로 새 요청을 생성합니다.

---

### 실패 처리 전략

실패는 두 구간에서 발생합니다: Mock Worker 제출 시(PENDING → PROCESSING)와 폴링 시(PROCESSING → COMPLETED/FAILED).

**에러 유형 분류**

| 에러 유형 | 처리 방식 |
|-----------|-----------|
| 4xx (429 제외) | 재시도해도 동일하게 실패 → 즉시 FAILED |
| 429 / 5xx / 네트워크 오류 | 일시적 원인 가능성 → Exponential Backoff 재시도 |

**재시도 전략 (제출 / 폴링 공통)**

```
1차 실패 → 1초 후 재시도
2차 실패 → 2초 후 재시도
3차 실패 → 4초 후 재시도
3번 모두 실패 → FAILED
```

Exponential Backoff를 선택한 이유는 일시적 오류(429, 5xx)의 경우 즉시 재시도하면 이미 부하 상태인 Worker에 요청을 더 쏟아 상황을 악화시킬 수 있기 때문입니다. 재시도 간격을 점진적으로 늘려 Worker가 회복할 시간을 확보합니다. 3회(누적 대기 7초)면 일시적 오류에서 회복하기에 충분하다고 판단했습니다.

---

### 동시 요청 발생 시 고려 사항

**Idempotency-Key 전략**: 클라이언트가 요청 헤더에 UUID v4를 포함합니다.

- 같은 Key로 재요청 → 기존 Job 그대로 반환 (멱등성 보장)
- 새로운 Key로 요청 → 같은 imageUrl이라도 새 Job 생성 (의도적 재처리 허용)

**동시성 처리**: `idempotency_key` 컬럼에 unique 설정을 적용합니다. 동시에 같은 Key로 요청이 들어올 경우 하나는 insert에 성공하고 하나는 DB unique constraint 위반 예외를 받습니다. 이 예외를 잡아 기존 Job을 조회하여 반환합니다. 중복 방어를 어플리케이션 레벨의 select 후 insert가 아닌 DB constraint에 의존함으로써 동시성 문제를 DB가 보장하도록 합니다.

**극단적 동시성 케이스**: constraint 위반 후 SELECT를 시도했을 때 아직 commit되지 않아 row가 없는 경우 HTTP 202를 반환하여 클라이언트가 잠시 후 재요청하도록 유도합니다. 발생 확률이 매우 낮으나 명시적으로 처리합니다.

**Redis 기반 중복 방지 미채택 이유**: Redis의 단일 스레드 특성을 활용한 `SETNX` 방식으로 더 깔끔하게 중복을 방어할 수 있습니다. 그러나 예상 사용자 규모와 인프라 구성을 알 수 없는 상황에서 Redis를 추가하는 것은 오버엔지니어링이라 판단했습니다. DB constraint만으로 동시성 문제를 충분히 처리할 수 있는 현재 규모에서는 단일 인프라(PostgreSQL)로 유지하는 것이 적절하다고 생각했습니다.

---

### 트래픽 증가 시 병목 가능 지점

**POST /api/jobs**: 요청마다 unique 체크 + insert가 발생합니다. 동시 요청이 몰릴 경우 DB 커넥션 풀이 빠르게 소진됩니다. 같은 Key로 동시 요청이 들어오면 constraint 충돌 후 재조회까지 DB를 2회 hit하여 경합이 심화됩니다.

**GET /api/jobs/{jobId}**: PK 기반 조회라 DB 부하는 낮습니다. 다만 클라이언트가 작업 완료를 확인하기 위해 짧은 주기로 반복 호출하면 Job 수에 비례해 요청이 급증할 수 있습니다.

**GET /api/jobs**: Job이 쌓일수록 전체 목록 조회 쿼리가 느려집니다. 페이지네이션으로 이를 해결했습니다.

**내부 폴러 (Scheduler)**: `PROCESSING` 상태 Job 전체를 주기적으로 조회하므로 `status` 컬럼에 인덱스가 필요합니다(수정). 순차 폴링 방식을 채택했으나 `PROCESSING` Job이 대량으로 쌓이면 1회 폴링 사이클이 길어집니다. `PENDING` Job이 대량으로 쌓인 경우 한 번에 Mock Worker에 제출을 시도하면 429를 유발할 수 있습니다.

---

### 외부 시스템과의 연동 방식 및 선택 이유

| 항목 | 채택 | 이유                                                                                     |
|------|------|----------------------------------------------------------------------------------------|
| 연동 방식 | HTTP | Mock Worker가 HTTP API로만 제공                                                             |
| 완료 감지 | 폴링 | Mock Worker가 Webhook을 지원하지 않아 폴링 방식 선택                                                 |
| 폴링 방식 | 동기(순차) | Mock Worker는 GPU 기반 무거운 AI 추론 작업으로 응답 시간이 수 초~수십 초까지 변동되며 순간 트래픽에 취약합니다. 비동기 동시 폴링 시 부하가 집중되어 429 및 불안정한 응답을 유발할 수 있어 순차 처리로 부하를 제어합니다. |
| HTTP 클라이언트 | WebClient | RestTemplate은 Spring 6부터 deprecated. Feign Client는 동기 방식이라 추후 순차 → 병렬 폴링 전환 시 대응 불가. WebClient는 비동기를 지원하므로 병렬 전환이 가능하며 `.retryWhen()`으로 재시도 로직을 선언적으로 표현 가능 |

**순차 → 병렬 전환 고려 시점**: 1회 폴링 사이클이 원하는 퍼포먼스 기준을 충족하지 못할 경우 순차 처리의 한계로 판단합니다. 이 경우 전체를 병렬로 전환하는 것이 아니라 스레드 수를 제한(예: 2개)하여 Worker가 수용 가능한 동시 요청 수를 실제 부하 테스트를 통해 확인한 뒤 그에 맞게 조정할 것입니다.

---

### 처리 보장 모델: At-least-once

| 구간 | 보장 수준 | 설명 |
|------|-----------|------|
| 클라이언트 → 내 서버 | Exactly-once에 가까움 | Idempotency-Key로 중복 방어 |
| 내 서버 → Mock Worker | At-least-once | 제출 후 DB 업데이트 전 장애 시 재제출 가능 |

**At-least-once가 보장되는 이유**: Mock Worker 제출 전에 이미 DB에 `PENDING`으로 저장되어 있고, 스케줄러가 `PENDING` Job을 지속적으로 재시도합니다. 제출 도중 서버가 재시작되더라도 DB에 남아있는 `PENDING` 상태를 기반으로 재제출이 발생하므로, 처리가 한 번도 이루어지지 않는 경우는 없습니다.

**Exactly-once가 불가능한 이유**: HTTP 호출은 DB 트랜잭션에 참여하지 않으므로, Mock Worker 호출이 성공한 뒤 DB 업데이트가 실패해도 HTTP 요청은 롤백되지 않습니다. 또한 Mock Worker 자체가 중복 제출에 대한 exactly-once를 보장하지 않아, 같은 작업이 두 번 제출되면 두 번 처리될 수 있습니다. 

**결론**: At-least-once를 기본으로 삼고 멱등성으로 중복을 방어하는 방식을 채택했습니다.

---

### 서버 재시작 시 동작

서버에는 별도의 시작 복구 로직이 없습니다. 스케줄러가 재시작 후 첫 사이클부터 DB에 남아있는 상태를 기반으로 동작하며, 대부분의 경우 자연 복구됩니다.

| 시점 | 재시작 후 동작 |
|------|------|
| 요청 수신 ~ DB 저장 | 요청 완전 유실. 클라이언트가 동일 Idempotency-Key로 재시도하여 복구 |
| Mock Worker 제출 ~ DB 업데이트 | DB는 PENDING, Worker에는 이미 제출된 상태. 재시작 후 스케줄러가 PENDING을 감지하여 재제출 |
| PROCESSING 상태에서 재시작 | 재시작 후 폴러가 PROCESSING Job을 감지하여 폴링 재개. 자연 복구 |
| 폴링으로 완료 확인 ~ DB 업데이트 | DB는 PROCESSING, 실제론 완료. 다음 폴링 사이클에서 재확인 후 업데이트. 자연 복구 |

**데이터 정합성이 깨질 수 있는 지점**

서버 재시작 구간에서는 대부분 자연 복구되며, 데이터 정합성이 깨질 수 있는 지점은 **종료 상태(COMPLETED/FAILED) 기록 이후**입니다.

`COMPLETED`와 `FAILED`를 단방향 종료 상태로 도메인에서 정의했기 때문에 해당 상태 기록 이후 폴링을 중단합니다. 따라서 이후 Worker가 해당 잡의 상태를 변경하거나 만료/삭제하더라도 감지하지 않으며, DB의 종료 상태가 최종 값으로 유지됩니다.
