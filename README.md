# KNOTE Backend

## 1. Project Description

KNOTE는 AI 기반 회의 관리 서비스입니다.

회의 음성 또는 회의 스크립트를 입력받아 STT(Speech-to-Text), 회의 요약, 주요 결정사항(Key Decisions), 액션 아이템(Action Items)을 생성하고 관리할 수 있도록 지원합니다.

본 저장소는 Backend API 서버를 포함합니다.

---

## 2. Source Code Description

Backend 프로젝트는 Spring Boot 기반의 REST API 서버로 구현되었으며 다음과 같은 구조를 가집니다.

```text
src/main/java/com/aiw/backend
├── app
│   ├── controller
│   │   └── api
│   │       ├── actionItem
│   │       ├── auth
│   │       ├── comment
│   │       ├── invite
│   │       ├── mainpage
│   │       ├── meeting
│   │       ├── mypage
│   │       ├── project
│   │       └── team
│   │
│   └── model
│
├── infra
|   ├── ai
|   ├── auth
│   ├── config
│   ├── entity
│   ├── error
│   └── response
│
└── util
```

### Package Description

| Package    | Description                                |
| ---------- | ------------------------------------------ |
| actionItem | 회의 액션 아이템 생성 및 관리 API                      |
| auth       | 회원가입, 로그인, JWT 인증 및 OAuth2 관련 API          |
| comment    | 프로젝트 및 회의 댓글 기능 API                        |
| invite     | 팀 초대 및 초대 코드 관리 API                        |
| mainpage   | 메인 페이지 정보 조회 API                           |
| meeting    | 회의 생성, 조회, 수정 및 삭제 API                     |
| mypage     | 사용자 마이페이지 관련 API                           |
| project    | 프로젝트 생성 및 관리 API                           |
| team       | 팀 생성 및 멤버 관리 API                           |
| model      | Entity, DTO, Repository, Service 등 비즈니스 로직 |
| infra      | 보안, 설정, 외부 API 및 인프라 연동                    |
| util       | 공통 유틸리티 클래스                                |

본 프로젝트는 Controller - Service - Repository 구조를 기반으로 구현되었으며, Spring Security를 활용한 인증/인가와 OpenAI API를 활용한 회의 분석 기능을 제공합니다.


---

## 3. How to Build

### Requirements

* Java 21
* Gradle
* MySQL
* Redis

### Build

```bash
./gradlew clean build
```

빌드 결과는 아래 위치에 생성됩니다.

```text
build/libs/
```

---

## 4. How to Install

### Clone Repository

```bash
git clone https://github.com/ALLISWELL-Lab/aiw-knote-BE.git
```

### Database Creation

```sql
CREATE DATABASE knote;
```

### Redis Execution

```bash
redis-server
```

### Environment Variables

```env
DB_URL=
DB_USERNAME=
DB_PASSWORD=

REDIS_HOST=
REDIS_PORT=

JWT_SECRET=

OPENAI_API_KEY=

AWS_ACCESS_KEY=
AWS_SECRET_KEY=
AWS_S3_BUCKET=
```

### Run Server

```bash
./gradlew bootRun
```

---

## 5. How to Test

서버 실행 후 아래 URL 접속

```text
http://localhost:8080/swagger-ui/index.html
```

Swagger를 통해 API 테스트가 가능합니다.

---

## 6. Sample Data

별도의 샘플 데이터는 제공하지 않습니다.

테스트 시 사용자가 직접 업로드한 회의 음성 파일 및 회의 스크립트를 사용합니다.

---

## 7. Database Used

### MySQL

주요 데이터

* User
* Meeting
* Transcript
* Summary
* Action Item

### Redis

사용 목적

* 인증 정보 관리
* 토큰 관리
* 캐시 처리

---

## 8. Open Source Used

### Backend Framework

* Spring Boot
* Spring Security
* Spring Data JPA

### AI

* OpenAI API

### Storage

* AWS S3

### Authentication

* JWT
* OAuth2 Client

### Documentation

* Springdoc OpenAPI (Swagger)
