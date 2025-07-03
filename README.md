# 금융결제원 오픈뱅킹 OAuth 서버

오픈뱅킹 시스템을 위한 OAuth 2.0 인증 서버입니다.

## 🚀 기능

- OAuth 2.0 Authorization Code Grant 플로우
- JWT 기반 Access Token 및 Refresh Token 발급
- 토큰 검증 및 폐기
- Swagger UI를 통한 API 문서화

## 📋 요구사항

- Java 17+
- Gradle 7.5+
- Spring Boot 3.5.3

## 🛠 설치 및 실행

1. 프로젝트 클론
```bash
git clone <repository-url>
cd kftc
```

2. 애플리케이션 실행
```bash
./gradlew bootRun
```

3. API 문서 확인
- Swagger UI: http://34.47.102.221:8080/swagger-ui.html

## 🔐 OAuth 2.0 플로우

### 1. 인증 코드 발급
```http
GET /oauth2.0/authorize?response_type=code&client_id=kftc-openbanking-client&redirect_uri=http://34.47.102.221:8080/oauth2/callback&scope=read,write&user_id=test_user
```

### 2. 액세스 토큰 발급
```http
POST /oauth2.0/token
Content-Type: application/json

{
    "grantType": "authorization_code",
    "clientId": "kftc-openbanking-client",
    "clientSecret": "kftc-openbanking-secret",
    "code": "{authorization_code}",
    "redirectUri": "http://34.47.102.221:8080/oauth2/callback"
}
```

### 3. 토큰 갱신
```http
POST /oauth2.0/token
Content-Type: application/json

{
    "grantType": "refresh_token",
    "clientId": "kftc-openbanking-client",
    "clientSecret": "kftc-openbanking-secret",
    "refreshToken": "{refresh_token}"
}
```

### 4. 토큰 검증
```http
POST /oauth2.0/introspect
Authorization: Bearer {access_token}
```

### 5. 토큰 폐기
```http
POST /oauth2.0/revoke
Authorization: Bearer {access_token}
```

## ⚙️ 설정

주요 설정은 `application-setting.yml`에서 관리됩니다:

```yaml
oauth:
  client:
    client-id: kftc-openbanking-client
    client-secret: kftc-openbanking-secret
    redirect-uri: http://localhost:8080/oauth2/callback
    scope: read,write
  token:
    access-token-validity: 3600  # 1시간
    refresh-token-validity: 86400  # 24시간
    jwt-secret: your-jwt-secret-key
```

## 📊 데이터베이스

H2 인메모리 데이터베이스를 사용하며, 다음 테이블들이 자동 생성됩니다:

- `oauth_clients`: OAuth 클라이언트 정보
- `oauth_tokens`: 발급된 토큰 정보
- `authorization_codes`: 인증 코드 정보

## 🧪 테스트

기본 테스트 클라이언트가 자동으로 등록됩니다:
- Client ID: `kftc-openbanking-client`
- Client Secret: `kftc-openbanking-secret`
- Redirect URI: `http://localhost:8080/oauth2/callback`

## 📝 API 명세

Swagger UI를 통해 상세한 API 명세를 확인할 수 있습니다:
http://localhost:8080/swagger-ui.html

## 🔧 개발 환경

- Spring Boot 3.5.3
- Spring Security 6.x
- Spring Data JPA
- JWT (JJWT 0.12.3)
- H2 Database
- Swagger/OpenAPI 3

## 📄 라이센스

이 프로젝트는 금융결제원 오픈뱅킹 시스템 구현을 위한 프로젝트입니다. 