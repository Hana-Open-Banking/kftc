package com.kftc.oauth.controller;

import com.kftc.common.dto.BasicResponse;
import com.kftc.common.util.CiGenerator;
import com.kftc.oauth.domain.OAuthClient;
import com.kftc.oauth.dto.TokenRequest;
import com.kftc.oauth.dto.TokenResponse;
import com.kftc.oauth.service.OAuthService;
import com.kftc.user.service.PhoneVerificationService;
import com.kftc.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Tag(name = "OAuth 2.0", description = "오픈뱅킹 OAuth 2.0 인증 API")
public class OAuthController {
    
    private final OAuthService oAuthService;
    private final UserService userService;
    private final PhoneVerificationService phoneVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final CiGenerator ciGenerator;
    
    @Value("${oauth.client.redirect-uri}")
    private String configuredRedirectUri;
    
    // 인증 세션 임시 저장소 (실제 환경에서는 Redis 등 사용)
    private final Map<String, AuthSession> authSessions = new ConcurrentHashMap<>();
    
    /**
     * 오픈뱅킹 인증 시작 (휴대폰 인증)
     */
    @Operation(summary = "오픈뱅킹 인증 시작", description = "휴대폰 인증으로 시작하는 오픈뱅킹 인증 화면을 제공합니다.")
    @GetMapping("/2.0/authorize")
    public ResponseEntity<String> startOAuthFlow(
            @Parameter(description = "OAuth 2.0 인증 요청 시 반환되는 형태", required = true) 
            @RequestParam("response_type") String responseType,
            
            @Parameter(description = "오픈뱅킹에서 발급한 이용기관 앱의 Client ID", required = true) 
            @RequestParam("client_id") String clientId,
            
            @Parameter(description = "사용자인증이 성공하면 이용기관으로 연결되는 URL", required = true) 
            @RequestParam("redirect_uri") String redirectUri,
            
            @Parameter(description = "Access Token 권한 범위", required = true) 
            @RequestParam("scope") String scope,
            
            @Parameter(description = "CSRF 보안위험에 대응하기 위해 이용기관이 세팅하는 난수값", required = true) 
            @RequestParam("state") String state) {
        
        log.info("오픈뱅킹 인증 시작: clientId={}, redirectUri={}", clientId, redirectUri);
        
        // 파라미터 검증
        if (!"code".equals(responseType)) {
            throw new IllegalArgumentException("response_type은 'code'여야 합니다.");
        }
        
        // 클라이언트 검증
        oAuthService.validateClient(clientId);
        
        // 인증 세션 생성
        String sessionId = generateSessionId();
        AuthSession session = new AuthSession(clientId, redirectUri, scope, state);
        authSessions.put(sessionId, session);
        
        // 휴대폰 인증 화면 HTML 반환
        String phoneAuthHtml = generatePhoneAuthHtml(sessionId);
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(phoneAuthHtml);
    }
    
    /**
     * 휴대폰 인증 코드 발송
     */
    @Operation(summary = "휴대폰 인증 코드 발송", description = "입력한 사용자 정보로 휴대폰 인증 코드를 발송합니다.")
    @PostMapping("/phone/send")
    public ResponseEntity<BasicResponse> sendPhoneVerificationCode(
            @Parameter(description = "인증 세션 ID", required = true)
            @RequestParam("session_id") String sessionId,
            
            @Parameter(description = "휴대폰 번호", required = true)
            @RequestParam("phone_number") String phoneNumber,
            
            @Parameter(description = "사용자 이름", required = true)
            @RequestParam("user_name") String userName,
            
            @Parameter(description = "사용자 이메일", required = true)
            @RequestParam("user_email") String userEmail,
            
            @Parameter(description = "주민등록번호", required = true)
            @RequestParam("social_security_number") String socialSecurityNumber) {
        
        log.info("휴대폰 인증 코드 발송: sessionId={}, phoneNumber={}, userName={}, userEmail={}, socialSecurityNumber={}", 
                sessionId, phoneNumber, userName, userEmail, socialSecurityNumber.substring(0, 6) + "******");
        
        // 세션 검증
        AuthSession session = authSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("유효하지 않은 세션입니다.");
        }
        
        // 휴대폰 인증 코드 발송
        phoneVerificationService.sendVerificationCode(phoneNumber);
        
        // 세션에 사용자 정보 저장
        session.setPhoneNumber(phoneNumber);
        session.setUserName(userName);
        session.setUserEmail(userEmail);
        session.setSocialSecurityNumber(socialSecurityNumber);
        
        BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("인증 코드가 발송되었습니다.")
                .data(null)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 휴대폰 인증 코드 확인 후 서비스 동의로 이동
     */
    @Operation(summary = "휴대폰 인증 코드 확인", description = "휴대폰 인증 완료 후 서비스 이용 동의 화면으로 이동합니다.")
    @PostMapping("/phone/verify")
    public ResponseEntity<String> verifyPhoneCode(
            @Parameter(description = "인증 세션 ID", required = true)
            @RequestParam("session_id") String sessionId,
            
            @Parameter(description = "휴대폰 번호", required = true)
            @RequestParam("phone_number") String phoneNumber,
            
            @Parameter(description = "인증 코드", required = true)
            @RequestParam("verification_code") String verificationCode) {
        
        log.info("휴대폰 인증 코드 확인: sessionId={}, phoneNumber={}", sessionId, phoneNumber);
        
        // 세션 검증
        AuthSession session = authSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("유효하지 않은 세션입니다.");
        }
        
        // 휴대폰 인증 코드 확인
        try {
            // 세션에서 사용자 정보를 가져와서 PASS 인증 처리 (실제 사용자 입력 주민등록번호 사용)
            String userSocialSecurityNumber = session.getSocialSecurityNumber();
            if (userSocialSecurityNumber == null || userSocialSecurityNumber.isEmpty()) {
                throw new IllegalArgumentException("주민등록번호가 없습니다.");
            }
            Object result = phoneVerificationService.verifyCodeWithPassAuth(phoneNumber, verificationCode, session.getUserName(), userSocialSecurityNumber);
            session.setPhoneVerified(true);
            
            // KISA 규격에 맞는 CI 생성 및 사용자 정보 생성/조회
            String tempUserCi = ciGenerator.generateCiWithRealRn(userSocialSecurityNumber);
            String userId = oAuthService.processUserAuth(tempUserCi, session.getUserName(), phoneNumber, session.getUserEmail());
            session.setUserId(userId);
            session.setUserCi(tempUserCi);
            
            // 바로 서비스 이용 동의 화면으로 이동
            String consentHtml = generateConsentHtml(sessionId, session);
            
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(consentHtml);
                    
        } catch (Exception e) {
            log.error("휴대폰 인증 실패: {}", e.getMessage());
            
            // 에러 화면 반환
            String errorHtml = generateErrorHtml(sessionId, "휴대폰 인증에 실패했습니다. 다시 시도해주세요.");
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(errorHtml);
        }
    }
    
    /**
     * 서비스 이용 동의 처리
     */
    @Operation(summary = "서비스 이용 동의 처리", description = "사용자 동의 후 Authorization Code를 발급하고 리디렉트합니다.")
    @PostMapping("/consent")
    public ResponseEntity<Void> processConsent(
            @Parameter(description = "인증 세션 ID", required = true)
            @RequestParam("session_id") String sessionId,
            
            @Parameter(description = "동의 여부", required = true)
            @RequestParam("agreed") boolean agreed) {
        
        log.info("서비스 이용 동의 처리 시작: sessionId={}, agreed={}", sessionId, agreed);
        
        try {
            // 세션 검증
            AuthSession session = authSessions.get(sessionId);
            if (session == null) {
                log.error("유효하지 않은 세션: sessionId={}", sessionId);
                
                // 이미 처리된 요청일 가능성이 높으므로 사용자에게 안내
                String message = "이미 처리된 요청입니다. 새로운 인증을 시작해주세요.";
                String redirectUrl = "/oauth/2.0/authorize?response_type=code&client_id=kftc-openbanking-client&redirect_uri=" +
                    URLEncoder.encode("http://34.47.102.221:8080/oauth/callback", StandardCharsets.UTF_8) +
                    "&scope=login|inquiry&state=" + URLEncoder.encode("new_" + System.currentTimeMillis(), StandardCharsets.UTF_8);
                
                return ResponseEntity.status(302)
                        .location(URI.create(redirectUrl))
                        .build();
            }
            
            log.info("세션 정보: clientId={}, redirectUri={}, userId={}, phoneVerified={}", 
                    session.getClientId(), session.getRedirectUri(), session.getUserId(), session.isPhoneVerified());
            
            if (!session.isPhoneVerified()) {
                log.error("휴대폰 인증 미완료: sessionId={}", sessionId);
                throw new IllegalArgumentException("휴대폰 인증이 완료되지 않았습니다.");
            }
            
            if (session.getUserId() == null || session.getUserId().isEmpty()) {
                log.error("사용자 ID 없음: sessionId={}", sessionId);
                throw new IllegalArgumentException("사용자 ID가 설정되지 않았습니다.");
            }
            
            if (!agreed) {
                // 동의하지 않은 경우 에러와 함께 리디렉트
                String errorUrl = session.getRedirectUri() + 
                    "?error=access_denied&error_description=User%20denied%20access&state=" + 
                    URLEncoder.encode(session.getState(), StandardCharsets.UTF_8);
                
                log.info("사용자 동의 거부, 에러 리디렉트: {}", errorUrl);
                authSessions.remove(sessionId);
                return ResponseEntity.status(302)
                        .location(URI.create(errorUrl))
                        .build();
            }
            
            // Authorization Code 발급
            log.info("Authorization Code 발급 시작: clientId={}, userId={}, scope={}, redirectUri={}", 
                    session.getClientId(), session.getUserId(), session.getScope(), session.getRedirectUri());
            
            String authorizationCode = oAuthService.generateAuthorizationCode(
                    session.getClientId(), session.getUserId(), session.getScope(), session.getRedirectUri());
            
            log.info("Authorization Code 발급 완료: code={}", authorizationCode);
            
            // 클라이언트로 리디렉트
            String redirectUrl = session.getRedirectUri() + 
                "?code=" + URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8) +
                "&state=" + URLEncoder.encode(session.getState(), StandardCharsets.UTF_8);
            
            log.info("동의 처리 완료, 리디렉트 URL: {}", redirectUrl);
            
            // 세션 정리
            authSessions.remove(sessionId);
            
            return ResponseEntity.status(302)
                    .location(URI.create(redirectUrl))
                    .build();
                    
        } catch (Exception e) {
            log.error("동의 처리 중 오류 발생: sessionId={}", sessionId, e);
            
            // 세션 정리
            authSessions.remove(sessionId);
            
            // 에러 응답 (디버깅용)
            throw new RuntimeException("동의 처리 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 액세스 토큰 발급 처리
     */
    @Operation(summary = "액세스 토큰 발급", description = "Authorization Code를 사용하여 액세스 토큰을 발급합니다.")
    @PostMapping(value = "/2.0/token", produces = "application/json")
    public ResponseEntity<TokenResponse> token(
            @Parameter(description = "사용자인증 성공 후 획득한 Authorization Code", required = true) 
            @RequestParam("code") String code,
            
            @Parameter(description = "오픈뱅킹에서 발급한 이용기관 앱의 Client ID", required = true) 
            @RequestParam("client_id") String clientId,
            
            @Parameter(description = "오픈뱅킹에서 발급한 이용기관 앱의 Client Secret", required = true) 
            @RequestParam("client_secret") String clientSecret,
            
            @Parameter(description = "Access Token을 전달받을 Callback URL", required = true) 
            @RequestParam("redirect_uri") String redirectUri,
            
            @Parameter(description = "권한부여 방식 (고정값: authorization_code)", required = true) 
            @RequestParam("grant_type") String grantType) {
        
        log.info("토큰 발급 요청: code={}, clientId={}, redirectUri={}", 
                code.substring(0, Math.min(code.length(), 10)) + "...", clientId, redirectUri);
        
        try {
            // TokenRequest 객체 생성
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setCode(code);
            tokenRequest.setClientId(clientId);
            tokenRequest.setClientSecret(clientSecret);
            tokenRequest.setRedirectUri(redirectUri);
            tokenRequest.setGrantType(grantType);
            
            // 토큰 발급
            TokenResponse tokenResponse = oAuthService.issueTokenByAuthCode(tokenRequest);
            
            log.info("토큰 발급 성공: accessToken={}, tokenType={}", 
                    tokenResponse.getAccessToken().substring(0, Math.min(tokenResponse.getAccessToken().length(), 10)) + "...",
                    tokenResponse.getTokenType());
            
            return ResponseEntity.ok(tokenResponse);
            
        } catch (Exception e) {
            log.error("토큰 발급 실패: code={}", code, e);
            throw e;
        }
    }
    
    /**
     * 토큰 검증
     */
    @Operation(summary = "토큰 검증", description = "Access Token의 유효성을 검증합니다.", 
               security = {@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "BearerAuth")})
    @PostMapping("/introspect")
    public ResponseEntity<BasicResponse> introspect(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization) {
        
        try {
            log.info("토큰 검증 요청");
            
            // Bearer 토큰에서 실제 토큰 추출
            String token;
            if (authorization.startsWith("Bearer ")) {
                token = authorization.substring(7); // "Bearer " 제거
            } else {
                token = authorization; // 토큰만 입력된 경우
            }
            
            log.info("토큰 검증 시작: {}", token.substring(0, Math.min(token.length(), 20)) + "...");
            
            // 토큰 유효성 검증
            boolean isValid = oAuthService.validateAccessToken(token);
            
            if (isValid) {
                log.info("토큰 검증 성공");
                return ResponseEntity.ok(BasicResponse.builder()
                        .status(200)
                        .message("토큰이 유효합니다.")
                        .data(null)
                        .build());
            } else {
                log.warn("토큰 검증 실패");
                return ResponseEntity.badRequest().body(BasicResponse.builder()
                        .status(400)
                        .message("유효하지 않은 토큰입니다.")
                        .data(null)
                        .build());
            }
            
        } catch (Exception e) {
            log.error("토큰 검증 중 오류 발생", e);
            return ResponseEntity.badRequest().body(BasicResponse.builder()
                    .status(500)
                    .message("토큰 검증 실패")
                    .data(null)
                    .build());
        }
    }
    
    /**
     * 표준 OAuth 2.0 Callback 엔드포인트
     */
    @Operation(summary = "OAuth 2.0 Callback", description = "표준 OAuth 2.0 Authorization Code를 받는 엔드포인트입니다.")
    @GetMapping("/2.0/callback")
    public ResponseEntity<String> oauthCallback(
            @Parameter(description = "Authorization Code", required = false)
            @RequestParam(value = "code", required = false) String code,
            
            @Parameter(description = "State", required = false)
            @RequestParam(value = "state", required = false) String state,
            
            @Parameter(description = "Error", required = false)
            @RequestParam(value = "error", required = false) String error,
            
            @Parameter(description = "Error Description", required = false)
            @RequestParam(value = "error_description", required = false) String errorDescription) {
        
        log.info("📋 OAuth 2.0 Callback 수신: code={}, state={}, error={}", 
                code != null ? code.substring(0, Math.min(code.length(), 10)) + "..." : null, 
                state, error);
        
        if (error != null) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(generateOAuthCallbackErrorHtml(error, errorDescription, state));
        }
        
        if (code == null || code.isEmpty()) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(generateOAuthCallbackErrorHtml("invalid_request", "Authorization Code가 없습니다.", state));
        }
        
        // Authorization Code 수신 성공
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(generateOAuthCallbackSuccessHtml(code, state));
    }
    
    // =============== 테스트용 클라이언트 시뮬레이터 ===============

    /**
     * 테스트용 클라이언트 시뮬레이터 - OAuth 인증 시작
     */
    @Operation(summary = "테스트 클라이언트", description = "실제 클라이언트 앱을 시뮬레이션하는 테스트 페이지입니다.")
    @GetMapping("/test/client")
    public ResponseEntity<String> testClient() {
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(generateTestClientHtml());
    }

    /**
     * 테스트용 클라이언트 시뮬레이터 - Authorization Code 수신
     */
    @Operation(summary = "테스트 클라이언트 Callback", description = "테스트 클라이언트가 Authorization Code를 받는 엔드포인트입니다.")
    @GetMapping("/test/callback")
    public ResponseEntity<String> testClientCallback(
            @Parameter(description = "Authorization Code", required = false)
            @RequestParam(value = "code", required = false) String code,
            
            @Parameter(description = "State", required = false)
            @RequestParam(value = "state", required = false) String state,
            
            @Parameter(description = "Error", required = false)
            @RequestParam(value = "error", required = false) String error,
            
            @Parameter(description = "Error Description", required = false)
            @RequestParam(value = "error_description", required = false) String errorDescription) {
        
        log.info("🧪 테스트 클라이언트 Callback 수신: code={}, state={}, error={}", 
                code != null ? code.substring(0, Math.min(code.length(), 10)) + "..." : null, 
                state, error);
        
        if (error != null) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(generateTestCallbackErrorHtml(error, errorDescription, state));
        }
        
        if (code == null || code.isEmpty()) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(generateTestCallbackErrorHtml("invalid_request", "Authorization Code가 없습니다.", state));
        }
        
        // Authorization Code 수신 성공 - 자동으로 토큰 발급 시도
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(generateTestCallbackSuccessHtml(code, state));
    }
    
    // =============== 클라이언트 관리 API ===============
    
    /**
     * 클라이언트 등록 신청 페이지
     */
    @Operation(summary = "클라이언트 등록", description = "새로운 OAuth 클라이언트를 등록하는 페이지입니다.")
    @GetMapping("/register/client")
    public ResponseEntity<String> clientRegistrationForm() {
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(generateClientRegistrationHtml());
    }

    /**
     * 클라이언트 등록 처리
     */
    @Operation(summary = "클라이언트 등록 처리", description = "새로운 OAuth 클라이언트를 등록합니다.")
    @PostMapping("/register/client")
    public ResponseEntity<BasicResponse> registerClient(
            @Parameter(description = "클라이언트명", required = true)
            @RequestParam("client_name") String clientName,

            @Parameter(description = "Redirect URI", required = true)
            @RequestParam("redirect_uri") String redirectUri,

            @Parameter(description = "서비스 도메인", required = true)
            @RequestParam("service_domain") String serviceDomain,

            @Parameter(description = "담당자 이메일", required = true)
            @RequestParam("contact_email") String contactEmail,

            @Parameter(description = "서비스 설명", required = false)
            @RequestParam(value = "description", required = false) String description) {

        log.info("클라이언트 등록 요청: clientName={}, redirectUri={}, serviceDomain={}",
                clientName, redirectUri, serviceDomain);

        try {
            // 입력값 검증
            if (clientName == null || clientName.trim().isEmpty()) {
                throw new IllegalArgumentException("클라이언트명은 필수입니다.");
            }

            if (redirectUri == null || redirectUri.trim().isEmpty()) {
                throw new IllegalArgumentException("Redirect URI는 필수입니다.");
            }

            if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
                throw new IllegalArgumentException("Redirect URI는 http:// 또는 https://로 시작해야 합니다.");
            }

            // 도메인 검증
            if (serviceDomain == null || serviceDomain.trim().isEmpty()) {
                throw new IllegalArgumentException("서비스 도메인은 필수입니다.");
            }

            // redirect_uri와 service_domain이 일치하는지 확인
            if (!redirectUri.contains(serviceDomain)) {
                throw new IllegalArgumentException("Redirect URI와 서비스 도메인이 일치하지 않습니다.");
            }

            // 클라이언트 ID/Secret 생성
            String clientId = generateClientId();
            String clientSecret = generateClientSecret();

            // 클라이언트 등록 (실제 구현에서는 승인 대기 상태로 설정)
            OAuthClient newClient = OAuthClient.builder()
                    .clientId(clientId)
                    .clientSecret(passwordEncoder.encode(clientSecret))
                    .clientName(clientName.trim())
                    .redirectUri(redirectUri.trim())
                    .scope("login|inquiry") // 기본 스코프 (파이프 문자 그대로 저장)
                    .isActive(false) // 승인 대기 상태
                    .clientUseCode("PENDING") // 승인 대기
                    .build();

            // DB에 저장하는 대신 로그로 출력 (실제 서비스에서는 DB 저장)
            log.info("🔐 새 클라이언트 등록 완료:");
            log.info("  - Client ID: {}", clientId);
            log.info("  - Client Secret: {}", clientSecret);
            log.info("  - Client Name: {}", clientName);
            log.info("  - Redirect URI: {}", redirectUri);
            log.info("  - Service Domain: {}", serviceDomain);
            log.info("  - Contact Email: {}", contactEmail);
            log.info("  - Description: {}", description);
            log.info("  - Scope: login|inquiry");

            return ResponseEntity.ok(BasicResponse.builder()
                    .status(200)
                    .message("클라이언트 등록이 완료되었습니다. 승인까지 1-2일 소요됩니다.")
                    .data(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "scope", "login|inquiry",
                            "status", "PENDING",
                            "message", "관리자 승인 후 사용 가능합니다."
                    ))
                    .build());

        } catch (Exception e) {
            log.error("클라이언트 등록 실패", e);
            return ResponseEntity.badRequest().body(BasicResponse.builder()
                    .status(400)
                    .message("클라이언트 등록 실패: " + e.getMessage())
                    .data(null)
                    .build());
        }
    }
    
    // 유틸리티 메서드들
    private String generateSessionId() {
        return java.util.UUID.randomUUID().toString();
    }
    
    private String generatePhoneAuthHtml(String sessionId) {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>오픈뱅킹 인증</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 20px; background-color: #f5f5f5; }
                    .auth-container { max-width: 400px; margin: 0 auto; padding: 30px; background: white; border-radius: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                    .step-indicator { display: flex; justify-content: center; margin-bottom: 30px; }
                    .step { width: 30px; height: 30px; border-radius: 50%%; margin: 0 20px; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; }
                    .step.active { background-color: #007bff; }
                    .step.inactive { background-color: #dee2e6; color: #6c757d; }
                    .form-group { margin: 20px 0; text-align: left; }
                    .form-group label { display: block; margin-bottom: 5px; font-weight: bold; }
                    .form-group input { width: 100%%; padding: 12px; border: 1px solid #ddd; border-radius: 5px; font-size: 16px; }
                    .btn { background-color: #007bff; color: white; padding: 12px 24px; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; width: 100%%; margin: 10px 0; }
                    .btn:hover { background-color: #0056b3; }
                    .btn:disabled { background-color: #6c757d; cursor: not-allowed; }
                    .verification-step { display: none; }
                    .info-text { font-size: 14px; color: #666; margin: 10px 0; }
                </style>
            </head>
            <body>
                <div class="auth-container">
                    <h2>🏦 오픈뱅킹 인증</h2>
                    
                    <div class="step-indicator">
                        <div class="step active">1</div>
                        <div class="step inactive">2</div>
                    </div>
                    
                    <div id="phone-step">
                        <h3>📱 사용자 정보 입력</h3>
                        <p class="info-text">본인 명의의 휴대폰으로 인증을 진행합니다.</p>
                        
                        <div class="form-group">
                            <label for="userName">이름</label>
                            <input type="text" id="userName" placeholder="홍길동" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="userEmail">이메일</label>
                            <input type="email" id="userEmail" placeholder="hong@example.com" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="socialSecurityNumber">주민등록번호</label>
                            <input type="text" id="socialSecurityNumber" placeholder="901010-1234567" maxlength="14" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="phoneNumber">휴대폰 번호</label>
                            <input type="tel" id="phoneNumber" placeholder="010-1234-5678" maxlength="13" required>
                        </div>
                        
                        <button class="btn" onclick="sendVerificationCode()">인증번호 발송</button>
                    </div>
                    
                    <div id="verification-step" class="verification-step">
                        <h3>📨 인증번호 확인</h3>
                        <p class="info-text">발송된 인증번호를 입력해주세요.</p>
                        
                        <div class="form-group">
                            <label for="verificationCode">인증번호</label>
                            <input type="text" id="verificationCode" placeholder="6자리 숫자" maxlength="6">
                </div>
                
                        <button class="btn" onclick="verifyCode()">인증번호 확인</button>
                        <button class="btn" style="background-color: #6c757d;" onclick="resendCode()">재발송</button>
                    </div>
                </div>
                
                <script>
                let sessionId = '%s';
                let phoneNumber = '';
                
                function formatPhoneNumber(value) {
                    const numbers = value.replace(/[^\\d]/g, '');
                    if (numbers.length <= 3) return numbers;
                    if (numbers.length <= 7) return numbers.slice(0, 3) + '-' + numbers.slice(3);
                    return numbers.slice(0, 3) + '-' + numbers.slice(3, 7) + '-' + numbers.slice(7, 11);
                }
                
                document.getElementById('phoneNumber').addEventListener('input', function(e) {
                    e.target.value = formatPhoneNumber(e.target.value);
                });
                
                function formatSocialSecurityNumber(value) {
                    const numbers = value.replace(/[^\\d]/g, '');
                    if (numbers.length <= 6) return numbers;
                    return numbers.slice(0, 6) + '-' + numbers.slice(6, 13);
                }
                
                document.getElementById('socialSecurityNumber').addEventListener('input', function(e) {
                    e.target.value = formatSocialSecurityNumber(e.target.value);
                });
                
                async function sendVerificationCode() {
                    const userName = document.getElementById('userName').value.trim();
                    const userEmail = document.getElementById('userEmail').value.trim();
                    const socialSecurityNumber = document.getElementById('socialSecurityNumber').value.replace(/[^\\d]/g, '');
                    phoneNumber = document.getElementById('phoneNumber').value.replace(/[^\\d]/g, '');
                    
                    if (!userName) {
                        alert('이름을 입력해주세요.');
                        return;
                    }
                    
                    if (!userEmail || !userEmail.includes('@')) {
                        alert('올바른 이메일을 입력해주세요.');
                        return;
                    }
                    
                    if (socialSecurityNumber.length !== 13) {
                        alert('올바른 주민등록번호를 입력해주세요. (13자리)');
                        return;
                    }
                    
                    if (phoneNumber.length !== 11) {
                        alert('올바른 휴대폰 번호를 입력해주세요.');
                        return;
                    }
                    
                                            try {
                            const response = await fetch('/oauth/phone/send', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                body: `session_id=${sessionId}&phone_number=${phoneNumber}&user_name=${encodeURIComponent(userName)}&user_email=${encodeURIComponent(userEmail)}&social_security_number=${socialSecurityNumber}`
                            });
                        
                        const result = await response.json();
                        if (response.ok) {
                            alert('인증번호가 발송되었습니다.');
                            document.getElementById('phone-step').style.display = 'none';
                            document.getElementById('verification-step').style.display = 'block';
                        } else {
                            alert('인증번호 발송에 실패했습니다: ' + result.message);
                        }
                    } catch (error) {
                        alert('네트워크 오류가 발생했습니다.');
                    }
                }
                
                async function verifyCode() {
                    const verificationCode = document.getElementById('verificationCode').value;
                    
                    if (verificationCode.length !== 6) {
                        alert('6자리 인증번호를 입력해주세요.');
                        return;
                    }
                    
                    try {
                        const response = await fetch('/oauth/phone/verify', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                            body: `session_id=${sessionId}&phone_number=${phoneNumber}&verification_code=${verificationCode}`
                        });
                        
                        if (response.ok) {
                            document.body.innerHTML = await response.text();
                        } else {
                            const result = await response.text();
                            document.body.innerHTML = result;
                        }
                    } catch (error) {
                        alert('네트워크 오류가 발생했습니다.');
                    }
                }
                
                function resendCode() {
                    document.getElementById('phone-step').style.display = 'block';
                    document.getElementById('verification-step').style.display = 'none';
                }
            </script>
        </body>
        </html>
            """.formatted(sessionId);
    }
    
    private String generateConsentHtml(String sessionId, AuthSession session) {
        log.debug("동의 페이지 HTML 생성: sessionId={}, session={}", sessionId, session);
        
        String html = """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>오픈뱅킹 서비스 이용 동의</title>
                <style>
                    body { font-family: Arial, sans-serif; padding: 20px; background-color: #f5f5f5; }
                    .consent-container { max-width: 600px; margin: 0 auto; padding: 30px; background: white; border-radius: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                    .step-indicator { display: flex; justify-content: center; margin-bottom: 30px; }
                    .step { width: 30px; height: 30px; border-radius: 50%%; margin: 0 20px; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; }
                    .step.completed { background-color: #28a745; }
                    .step.active { background-color: #007bff; }
                    .scope-list { text-align: left; margin: 20px 0; }
                    .scope-item { padding: 15px; margin: 10px 0; background-color: #f8f9fa; border-radius: 8px; border-left: 4px solid #007bff; }
                    .user-info { background-color: #e7f3ff; padding: 15px; border-radius: 8px; margin: 20px 0; text-align: left; }
                    .btn-group { margin-top: 30px; text-align: center; }
                    .btn { padding: 12px 24px; margin: 0 10px; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; }
                    .btn-primary { background-color: #007bff; color: white; }
                    .btn-secondary { background-color: #6c757d; color: white; }
                    .btn:hover { opacity: 0.8; }
                    .success-badge { color: #28a745; font-size: 12px; margin: 2px 0; }
                    .consent-form { display: inline-block; margin: 0 5px; }
                </style>
            </head>
            <body>
                <div class="consent-container">
                    <h2 style="text-align: center;">🏦 오픈뱅킹 서비스 이용 동의</h2>
                    
                    <div class="step-indicator">
                        <div class="step completed">✓</div>
                        <div class="step active">2</div>
                    </div>
                    
                    <div style="text-align: center;">
                        <div class="success-badge">✅ 휴대폰 인증 완료</div>
                    </div>
                    
                    <div class="user-info">
                        <h4>📋 인증 정보</h4>
                        <p><strong>휴대폰:</strong> %s</p>
                        <p><strong>클라이언트:</strong> %s</p>
                        <p><strong>세션 ID:</strong> %s</p>
                    </div>
                    
                    <p>다음 권한에 대한 동의가 필요합니다:</p>
                    
                    <div class="scope-list">
                        %s
                    </div>
                    
                    <div class="btn-group">
                        <form class="consent-form" method="POST" action="/oauth/consent" onsubmit="return handleSubmit(this, true)">
                            <input type="hidden" name="session_id" value="%s">
                            <input type="hidden" name="agreed" value="true">
                            <button type="submit" class="btn btn-primary" id="agreeBtn">동의하고 계속</button>
                        </form>
                        
                        <form class="consent-form" method="POST" action="/oauth/consent" onsubmit="return handleSubmit(this, false)">
                            <input type="hidden" name="session_id" value="%s">
                            <input type="hidden" name="agreed" value="false">
                            <button type="submit" class="btn btn-secondary" id="rejectBtn">거부</button>
                        </form>
                    </div>
                    
                    <div style="margin-top: 20px; text-align: center; font-size: 12px; color: #666;">
                        <p>세션 ID: %s</p>
                        <p>디버깅 정보 - 동의 페이지가 정상적으로 로드되었습니다.</p>
                    </div>
                </div>
                
                <script>
                    let isSubmitting = false;
                    
                    function handleSubmit(form, agreed) {
                        if (isSubmitting) {
                            alert('이미 처리 중입니다. 잠시만 기다려주세요.');
                            return false;
                        }
                        
                        isSubmitting = true;
                        
                        // 버튼 비활성화
                        const agreeBtn = document.getElementById('agreeBtn');
                        const rejectBtn = document.getElementById('rejectBtn');
                        
                        if (agreeBtn) {
                            agreeBtn.disabled = true;
                            agreeBtn.textContent = agreed ? '처리중...' : '동의하고 계속';
                        }
                        
                        if (rejectBtn) {
                            rejectBtn.disabled = true;
                            rejectBtn.textContent = agreed ? '거부' : '처리중...';
                        }
                        
                        return true;
                    }
                </script>
            </body>
            </html>
            """.formatted(
                session.getPhoneNumber() != null ? session.getPhoneNumber() : "인증완료",
                session.getClientId(),
                sessionId,
                generateScopeListHtml(session.getScope()),
                sessionId,
                sessionId,
                sessionId
            );
            
        log.debug("생성된 동의 페이지 HTML 길이: {}", html.length());
        return html;
    }
    
    private String generateErrorHtml(String sessionId, String errorMessage) {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>인증 오류</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; background-color: #f5f5f5; }
                    .error-container { max-width: 400px; margin: 0 auto; padding: 30px; background: white; border-radius: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); border-left: 4px solid #dc3545; }
                    .btn { background-color: #007bff; color: white; padding: 12px 24px; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; }
                    .btn:hover { background-color: #0056b3; }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <h2>❌ 인증 오류</h2>
                    <p>%s</p>
                    <button class="btn" onclick="history.back()">다시 시도</button>
                </div>
            </body>
            </html>
            """.formatted(errorMessage);
    }

    private String generateScopeListHtml(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return "<div class='scope-item'>📋 기본 권한</div>";
        }

        StringBuilder html = new StringBuilder();

        // 파이프(|)로 분리하여 각 scope를 개별 처리
        String[] scopes = scope.split("\\|");

        log.debug("Scope 분리 결과: 원본='{}', 분리된 개수={}", scope, scopes.length);

        for (String s : scopes) {
            String trimmedScope = s.trim(); // 공백 제거
            if (!trimmedScope.isEmpty()) {
                String description = getScopeDescription(trimmedScope);
                html.append("<div class='scope-item'>").append(description).append("</div>");
                log.debug("Scope 처리: '{}' -> '{}'", trimmedScope, description);
            }
        }

        return html.toString();
    }

    private String getScopeDescription(String scope) {
        return switch (scope.toLowerCase()) {
            case "login" -> "🔐 로그인 정보 확인";
            case "inquiry" -> "📊 계좌 잔액 및 거래내역 조회";
            case "transfer" -> "💸 계좌 이체 실행";
            default -> "📋 " + scope + " 권한";
        };
    }
    
    // 간소화된 인증 세션 클래스
    private static class AuthSession {
        private final String clientId;
        private final String redirectUri;
        private final String scope;
        private final String state;
        private String userId;
        private String userCi;
        private String userName;
        private String userEmail;
        private String phoneNumber;
        private String socialSecurityNumber;
        private boolean phoneVerified = false;
        
        public AuthSession(String clientId, String redirectUri, String scope, String state) {
            this.clientId = clientId;
            this.redirectUri = redirectUri;
            this.scope = scope;
            this.state = state;
        }
        
        // Getters and Setters
        public String getClientId() { return clientId; }
        public String getRedirectUri() { return redirectUri; }
        public String getScope() { return scope; }
        public String getState() { return state; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUserCi() { return userCi; }
        public void setUserCi(String userCi) { this.userCi = userCi; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getSocialSecurityNumber() { return socialSecurityNumber; }
        public void setSocialSecurityNumber(String socialSecurityNumber) { this.socialSecurityNumber = socialSecurityNumber; }
        public boolean isPhoneVerified() { return phoneVerified; }
        public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }
    }

    private String generateTestClientHtml() {
        return ("""
        <!DOCTYPE html>
        <html lang="ko">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>🧪 OAuth 테스트 클라이언트</title>
            <style>
                body { font-family: 'Malgun Gothic', Arial, sans-serif; text-align: center; padding: 30px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; margin: 0; }
                .client-container { max-width: 500px; margin: 0 auto; padding: 40px; background: white; border-radius: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.1); }
                .client-title { color: #333; margin-bottom: 30px; font-size: 28px; font-weight: bold; }
                .description { color: #666; margin-bottom: 30px; line-height: 1.6; }
                .oauth-btn { background: linear-gradient(45deg, #667eea, #764ba2); color: white; padding: 15px 30px; border: none; border-radius: 50px; cursor: pointer; font-size: 18px; font-weight: bold; width: 100%; margin: 10px 0; transition: transform 0.2s; }
                .oauth-btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(0,0,0,0.2); }
                .info-box { background-color: #e3f2fd; padding: 20px; border-radius: 10px; margin: 20px 0; text-align: left; }
                .info-title { font-weight: bold; color: #1976d2; margin-bottom: 10px; }
                .flow-step { background: #f8f9fa; padding: 15px; margin: 10px 0; border-radius: 8px; border-left: 4px solid #667eea; }
                .test-icon { font-size: 48px; margin-bottom: 20px; }
            </style>
        </head>
        <body>
            <div class="client-container">
                <div class="test-icon">🧪</div>
                <h1 class="client-title">OAuth 테스트 클라이언트</h1>
                <p class="description">
                    실제 클라이언트 앱을 시뮬레이션하여 OAuth 2.0 인증 플로우를 테스트합니다.
                </p>
                
                <div class="info-box">
                    <div class="info-title">📋 OAuth 플로우</div>
                    <div class="flow-step">1️⃣ 오픈뱅킹 로그인 버튼 클릭</div>
                    <div class="flow-step">2️⃣ 오픈뱅킹 센터로 리디렉트</div>
                    <div class="flow-step">3️⃣ 휴대폰 본인인증 수행</div>
                    <div class="flow-step">4️⃣ 서비스 이용 동의</div>
                    <div class="flow-step">5️⃣ Authorization Code 수신</div>  
                    <div class="flow-step">6️⃣ Access Token 자동 발급</div>
                </div>
                
                <button class="oauth-btn" onclick="startOAuthFlow()">
                    🏦 오픈뱅킹 로그인 시작
                </button>
                
                <div class="info-box">
                    <div class="info-title">ℹ️ 테스트 정보</div>
                    <p><strong>Client ID:</strong> kftc-openbanking-client</p>
                    <p><strong>Redirect URI:</strong> %s</p>
                    <p><strong>Scope:</strong> login|inquiry</p>
                </div>
            </div>
            
            <script>
                function startOAuthFlow() {
                    const state = 'test_' + Date.now();
                    
                    // scope 파이프 문자를 URL 인코딩
                    const scope = encodeURIComponent('login|inquiry');
                    
                    const authUrl = '/oauth/2.0/authorize?' + new URLSearchParams({
                        response_type: 'code',
                        client_id: 'kftc-openbanking-client',
                        redirect_uri: '%s',
                        scope: 'login|inquiry', // URLSearchParams가 자동으로 인코딩
                        state: state
                    });
                    
                    console.log('OAuth 인증 시작:', authUrl);
                    window.location.href = authUrl;
                }
            </script>
        </body>
        </html>
        """).formatted(configuredRedirectUri, configuredRedirectUri, configuredRedirectUri);
    }


    private String generateOAuthCallbackSuccessHtml(String code, String state) {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>🎉 OAuth 인증 성공</title>
                <style>
                    body { font-family: 'Malgun Gothic', Arial, sans-serif; text-align: center; padding: 30px; background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%); min-height: 100vh; margin: 0; }
                    .success-container { max-width: 700px; margin: 0 auto; padding: 40px; background: white; border-radius: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.1); }
                    .success-icon { font-size: 64px; margin-bottom: 20px; }
                    .success-title { color: #2e7d32; margin-bottom: 20px; font-size: 32px; font-weight: bold; }
                    .code-display { background: #f8f9fa; padding: 20px; border-radius: 10px; margin: 20px 0; font-family: 'Courier New', monospace; word-break: break-all; border: 2px solid #4CAF50; }
                    .token-btn { background: linear-gradient(45deg, #FF6B35, #F7931E); color: white; padding: 15px 30px; border: none; border-radius: 50px; cursor: pointer; font-size: 16px; font-weight: bold; margin: 10px; transition: transform 0.2s; }
                    .token-btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(0,0,0,0.2); }
                    .token-btn:disabled { background: #ccc; cursor: not-allowed; transform: none; }
                    .info-box { background-color: #e8f5e8; padding: 20px; border-radius: 10px; margin: 20px 0; text-align: left; }
                    .token-result { background: #fff3cd; padding: 15px; border-radius: 8px; margin: 15px 0; display: none; }
                    .loading { color: #666; font-style: italic; }
                    .swagger-info { background: #e3f2fd; padding: 15px; border-radius: 8px; margin: 15px 0; text-align: left; }
                </style>
            </head>
            <body>
                <div class="success-container">
                    <div class="success-icon">🎉</div>
                    <h1 class="success-title">OAuth 인증 성공!</h1>
                    <p>Authorization Code를 성공적으로 받았습니다.</p>
                    
                    <div class="info-box">
                        <h4>📨 수신 정보</h4>
                        <p><strong>State:</strong> %s</p>
                        <p><strong>수신 시간:</strong> %s</p>
                    </div>
                    
                    <h4>🔑 Authorization Code</h4>
                    <div class="code-display">%s</div>
                    
                    <div class="swagger-info">
                        <h4>🔧 Swagger UI에서 토큰 발급하기</h4>
                        <p>이제 <a href="/swagger-ui/index.html" target="_blank">Swagger UI</a>에서 다음 정보로 토큰을 발급받을 수 있습니다:</p>
                        <ul>
                            <li><strong>code:</strong> %s</li>
                            <li><strong>client_id:</strong> kftc-openbanking-client</li>
                            <li><strong>client_secret:</strong> kftc-openbanking-secret</li>
                            <li><strong>redirect_uri:</strong> %s</li>
                            <li><strong>grant_type:</strong> authorization_code</li>
                        </ul>
                    </div>
                    
                    <div class="info-box">
                        <h4>🚀 다음 단계: Access Token 발급</h4>
                        <p>실제 클라이언트에서는 서버에서 이 코드를 사용하여 토큰을 발급받습니다.</p>
                    </div>
                    
                    <button class="token-btn" onclick="issueToken()" id="tokenBtn">
                        ⚡ Access Token 자동 발급
                    </button>
                    <button class="token-btn" onclick="copyCode()">
                        📋 Authorization Code 복사
                    </button>
                    <button class="token-btn" onclick="openSwagger()">
                        📖 Swagger UI 열기
                    </button>
                    
                    <div id="tokenResult" class="token-result"></div>
                </div>
                
                <script>
                    const authCode = '%s';
                    const state = '%s';
                    
                    function copyCode() {
                        navigator.clipboard.writeText(authCode).then(function() {
                            alert('✅ Authorization Code가 클립보드에 복사되었습니다!');
                        }).catch(function(err) {
                            console.error('복사 실패:', err);
                            alert('❌ 복사에 실패했습니다. 코드를 직접 선택해주세요.');
                        });
                    }
                    
                    function openSwagger() {
                        window.open('/swagger-ui/index.html', '_blank');
                    }
                    
                    async function issueToken() {
                        const btn = document.getElementById('tokenBtn');
                        const result = document.getElementById('tokenResult');
                        
                        btn.disabled = true;
                        btn.textContent = '🔄 토큰 발급 중...';
                        result.style.display = 'block';
                        result.innerHTML = '<div class="loading">⏳ Access Token을 발급받고 있습니다...</div>';
                        
                        try {
                            const response = await fetch('/oauth/2.0/token', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                body: new URLSearchParams({
                                    code: authCode,
                                    client_id: 'kftc-openbanking-client',
                                    client_secret: 'kftc-openbanking-secret',
                                    redirect_uri: '%s',
                                    grant_type: 'authorization_code'
                                })
                            });
                            
                            if (response.ok) {
                                const tokenData = await response.json();
                                result.innerHTML = `
                                    <h4>✅ Access Token 발급 성공!</h4>
                                    <p><strong>Access Token:</strong></p>
                                    <div style="background: #f8f9fa; padding: 10px; border-radius: 5px; font-family: monospace; word-break: break-all; margin: 10px 0;">
                                        ${tokenData.access_token}
                                    </div>
                                    <p><strong>Token Type:</strong> ${tokenData.token_type}</p>
                                    <p><strong>Expires In:</strong> ${tokenData.expires_in}초</p>
                                    <p><strong>Scope:</strong> ${tokenData.scope || 'N/A'}</p>
                                    <p><strong>User Seq No:</strong> ${tokenData.user_seq_no || 'N/A'}</p>
                                `;
                                btn.textContent = '✅ 토큰 발급 완료';
                                btn.style.background = '#4CAF50';
                            } else {
                                const errorText = await response.text();
                                result.innerHTML = `<h4>❌ 토큰 발급 실패</h4><p>${errorText}</p>`;
                                btn.disabled = false;
                                btn.textContent = '⚡ Access Token 자동 발급';
                            }
                        } catch (error) {
                            result.innerHTML = `<h4>❌ 네트워크 오류</h4><p>${error.message}</p>`;
                            btn.disabled = false;
                            btn.textContent = '⚡ Access Token 자동 발급';
                        }
                    }
                </script>
            </body>
            </html>
            """.formatted(
                state != null ? state : "없음",
                java.time.LocalDateTime.now().toString(),
                code,
                code,
                configuredRedirectUri,
                code,
                state != null ? state : "없음",
                configuredRedirectUri
            );
    }
    
    private String generateTestCallbackSuccessHtml(String code, String state) {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>🎉 OAuth 인증 성공</title>
                <style>
                    body { font-family: 'Malgun Gothic', Arial, sans-serif; text-align: center; padding: 30px; background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%); min-height: 100vh; margin: 0; }
                    .success-container { max-width: 700px; margin: 0 auto; padding: 40px; background: white; border-radius: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.1); }
                    .success-icon { font-size: 64px; margin-bottom: 20px; }
                    .success-title { color: #2e7d32; margin-bottom: 20px; font-size: 32px; font-weight: bold; }
                    .code-display { background: #f8f9fa; padding: 20px; border-radius: 10px; margin: 20px 0; font-family: 'Courier New', monospace; word-break: break-all; border: 2px solid #4CAF50; }
                    .token-btn { background: linear-gradient(45deg, #FF6B35, #F7931E); color: white; padding: 15px 30px; border: none; border-radius: 50px; cursor: pointer; font-size: 16px; font-weight: bold; margin: 10px; transition: transform 0.2s; }
                    .token-btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(0,0,0,0.2); }
                    .token-btn:disabled { background: #ccc; cursor: not-allowed; transform: none; }
                    .info-box { background-color: #e8f5e8; padding: 20px; border-radius: 10px; margin: 20px 0; text-align: left; }
                    .token-result { background: #fff3cd; padding: 15px; border-radius: 8px; margin: 15px 0; display: none; }
                    .loading { color: #666; font-style: italic; }
                </style>
            </head>
            <body>
                <div class="success-container">
                    <div class="success-icon">🎉</div>
                    <h1 class="success-title">OAuth 인증 성공!</h1>
                    <p>Authorization Code를 성공적으로 받았습니다.</p>
                    
                    <div class="info-box">
                        <h4>📨 수신 정보</h4>
                        <p><strong>State:</strong> %s</p>
                        <p><strong>수신 시간:</strong> %s</p>
                    </div>
                    
                    <h4>🔑 Authorization Code</h4>
                    <div class="code-display">%s</div>
                    
                    <div class="info-box">
                        <h4>🚀 다음 단계: Access Token 발급</h4>
                        <p>실제 클라이언트에서는 서버에서 이 코드를 사용하여 토큰을 발급받습니다.</p>
                    </div>
                    
                    <button class="token-btn" onclick="issueToken()" id="tokenBtn">
                        ⚡ Access Token 자동 발급
                    </button>
                    <button class="token-btn" onclick="copyCode()">
                        📋 Authorization Code 복사
                    </button>
                    
                    <div id="tokenResult" class="token-result"></div>
                </div>
                
                <script>
                    const authCode = '%s';
                    const state = '%s';
                    
                    function copyCode() {
                        navigator.clipboard.writeText(authCode).then(function() {
                            alert('✅ Authorization Code가 클립보드에 복사되었습니다!');
                        }).catch(function(err) {
                            console.error('복사 실패:', err);
                            alert('❌ 복사에 실패했습니다. 코드를 직접 선택해주세요.');
                        });
                    }
                    
                    async function issueToken() {
                        const btn = document.getElementById('tokenBtn');
                        const result = document.getElementById('tokenResult');
                        
                        btn.disabled = true;
                        btn.textContent = '🔄 토큰 발급 중...';
                        result.style.display = 'block';
                        result.innerHTML = '<div class="loading">⏳ Access Token을 발급받고 있습니다...</div>';
                        
                        try {
                            const response = await fetch('/oauth/2.0/token', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                body: new URLSearchParams({
                                    code: authCode,
                                    client_id: 'kftc-openbanking-client',
                                    client_secret: 'kftc-openbanking-secret',
                                    redirect_uri: 'http://34.47.102.221:8080/oauth/test/callback',
                                    grant_type: 'authorization_code'
                                })
                            });
                            
                            if (response.ok) {
                                const tokenData = await response.json();
                                result.innerHTML = `
                                    <h4>✅ Access Token 발급 성공!</h4>
                                    <p><strong>Access Token:</strong></p>
                                    <div style="background: #f8f9fa; padding: 10px; border-radius: 5px; font-family: monospace; word-break: break-all; margin: 10px 0;">
                                        ${tokenData.access_token}
                                    </div>
                                    <p><strong>Token Type:</strong> ${tokenData.token_type}</p>
                                    <p><strong>Expires In:</strong> ${tokenData.expires_in}초</p>
                                    <p><strong>Scope:</strong> ${tokenData.scope || 'N/A'}</p>
                                `;
                                btn.textContent = '✅ 토큰 발급 완료';
                                btn.style.background = '#4CAF50';
                            } else {
                                const errorText = await response.text();
                                result.innerHTML = `<h4>❌ 토큰 발급 실패</h4><p>${errorText}</p>`;
                                btn.disabled = false;
                                btn.textContent = '⚡ Access Token 자동 발급';
                            }
                        } catch (error) {
                            result.innerHTML = `<h4>❌ 네트워크 오류</h4><p>${error.message}</p>`;
                            btn.disabled = false;
                            btn.textContent = '⚡ Access Token 자동 발급';
                        }
                    }
                </script>
            </body>
            </html>
            """.formatted(
                state != null ? state : "없음",
                java.time.LocalDateTime.now().toString(),
                code,
                code,
                state != null ? state : "없음"
            );
    }
    
    private String generateTestCallbackErrorHtml(String error, String errorDescription, String state) {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>❌ OAuth 인증 실패</title>
                <style>
                    body { font-family: 'Malgun Gothic', Arial, sans-serif; text-align: center; padding: 30px; background: linear-gradient(135deg, #f44336 0%, #d32f2f 100%); min-height: 100vh; margin: 0; }
                    .error-container { max-width: 500px; margin: 0 auto; padding: 40px; background: white; border-radius: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.1); }
                    .error-icon { font-size: 64px; margin-bottom: 20px; }
                    .error-title { color: #d32f2f; margin-bottom: 20px; font-size: 28px; font-weight: bold; }
                    .error-code { background: #ffebee; padding: 15px; border-radius: 8px; margin: 15px 0; font-family: 'Courier New', monospace; border: 2px solid #f44336; }
                    .retry-btn { background: linear-gradient(45deg, #2196F3, #21CBF3); color: white; padding: 15px 30px; border: none; border-radius: 50px; cursor: pointer; font-size: 16px; font-weight: bold; margin: 10px; transition: transform 0.2s; }
                    .retry-btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(0,0,0,0.2); }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <div class="error-icon">❌</div>
                    <h1 class="error-title">OAuth 인증 실패</h1>
                    <p>인증 중 오류가 발생했습니다.</p>
                    
                    <div class="error-code">
                        <strong>Error:</strong> %s<br>
                        <strong>Description:</strong> %s<br>
                        <strong>State:</strong> %s
                    </div>
                    
                    <button class="retry-btn" onclick="window.location.href='/oauth/test/client'">
                        🔄 다시 시도
                    </button>
                </div>
            </body>
            </html>
            """.formatted(
                error != null ? error : "unknown_error",
                errorDescription != null ? errorDescription : "알 수 없는 오류가 발생했습니다.",
                state != null ? state : "없음"
            );
    }
    
    private String generateOAuthCallbackErrorHtml(String error, String errorDescription, String state) {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>❌ OAuth 인증 실패</title>
                <style>
                    body { font-family: 'Malgun Gothic', Arial, sans-serif; text-align: center; padding: 30px; background: linear-gradient(135deg, #f44336 0%, #d32f2f 100%); min-height: 100vh; margin: 0; }
                    .error-container { max-width: 500px; margin: 0 auto; padding: 40px; background: white; border-radius: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.1); }
                    .error-icon { font-size: 64px; margin-bottom: 20px; }
                    .error-title { color: #d32f2f; margin-bottom: 20px; font-size: 28px; font-weight: bold; }
                    .error-code { background: #ffebee; padding: 15px; border-radius: 8px; margin: 15px 0; font-family: 'Courier New', monospace; border: 2px solid #f44336; }
                    .retry-btn { background: linear-gradient(45deg, #2196F3, #21CBF3); color: white; padding: 15px 30px; border: none; border-radius: 50px; cursor: pointer; font-size: 16px; font-weight: bold; margin: 10px; transition: transform 0.2s; }
                    .retry-btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(0,0,0,0.2); }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <div class="error-icon">❌</div>
                    <h1 class="error-title">OAuth 인증 실패</h1>
                    <p>인증 중 오류가 발생했습니다.</p>
                    
                    <div class="error-code">
                        <strong>Error:</strong> %s<br>
                        <strong>Description:</strong> %s<br>
                        <strong>State:</strong> %s
                    </div>
                    
                    <button class="retry-btn" onclick="window.location.href='/oauth/test/client'">
                        🔄 다시 시도
                    </button>
                </div>
            </body>
            </html>
            """.formatted(
                error != null ? error : "unknown_error",
                errorDescription != null ? errorDescription : "알 수 없는 오류가 발생했습니다.",
                state != null ? state : "없음"
            );
    }
    
    private String generateClientRegistrationHtml() {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>🔐 OAuth 클라이언트 등록</title>
                <style>
                    body { font-family: 'Malgun Gothic', Arial, sans-serif; text-align: center; padding: 30px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; margin: 0; }
                    .register-container { max-width: 600px; margin: 0 auto; padding: 40px; background: white; border-radius: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.1); }
                    .register-title { color: #333; margin-bottom: 30px; font-size: 28px; font-weight: bold; }
                    .description { color: #666; margin-bottom: 30px; line-height: 1.6; text-align: left; }
                    .form-group { margin: 20px 0; text-align: left; }
                    .form-group label { display: block; margin-bottom: 8px; font-weight: bold; color: #333; }
                    .form-group input, .form-group textarea { width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; font-size: 16px; }
                    .form-group input:focus, .form-group textarea:focus { border-color: #667eea; outline: none; box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2); }
                    .form-group .help-text { font-size: 12px; color: #666; margin-top: 5px; }
                    .register-btn { background: linear-gradient(45deg, #667eea, #764ba2); color: white; padding: 15px 30px; border: none; border-radius: 50px; cursor: pointer; font-size: 18px; font-weight: bold; width: 100%; margin: 20px 0; transition: transform 0.2s; }
                    .register-btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(0,0,0,0.2); }
                    .register-btn:disabled { background: #ccc; cursor: not-allowed; transform: none; }
                    .info-box { background-color: #e3f2fd; padding: 20px; border-radius: 10px; margin: 20px 0; text-align: left; }
                    .info-title { font-weight: bold; color: #1976d2; margin-bottom: 10px; }
                    .client-icon { font-size: 48px; margin-bottom: 20px; }
                    .required { color: #f44336; }
                </style>
            </head>
            <body>
                <div class="register-container">
                    <div class="client-icon">🔐</div>
                    <h1 class="register-title">OAuth 클라이언트 등록</h1>
                    <p class="description">
                        오픈뱅킹 API를 사용하기 위해 클라이언트 앱을 등록해주세요. 
                        등록 후 관리자 승인을 거쳐 Client ID와 Secret을 발급받을 수 있습니다.
                    </p>
                    
                    <div class="info-box">
                        <div class="info-title">📋 등록 절차</div>
                        <p>1️⃣ 클라이언트 정보 입력</p>
                        <p>2️⃣ 신청서 제출</p>
                        <p>3️⃣ 관리자 검토 (1-2일)</p>
                        <p>4️⃣ 승인 완료 후 Client ID/Secret 발급</p>
                    </div>
                    
                    <form id="clientForm" method="POST" action="/oauth/register/client">
                        <div class="form-group">
                            <label for="clientName">서비스명 <span class="required">*</span></label>
                            <input type="text" id="clientName" name="client_name" required 
                                   placeholder="예: 마이뱅크 앱">
                            <div class="help-text">사용자에게 표시될 서비스명을 입력하세요.</div>
                        </div>
                        
                        <div class="form-group">
                            <label for="serviceDomain">서비스 도메인 <span class="required">*</span></label>
                            <input type="text" id="serviceDomain" name="service_domain" required 
                                   placeholder="예: mybank.com">
                            <div class="help-text">서비스가 운영되는 도메인을 입력하세요.</div>
                        </div>
                        
                        <div class="form-group">
                            <label for="redirectUri">Redirect URI <span class="required">*</span></label>
                            <input type="url" id="redirectUri" name="redirect_uri" required 
                                   placeholder="https://mybank.com/oauth/callback">
                            <div class="help-text">OAuth 인증 완료 후 리디렉트될 URL입니다. 서비스 도메인과 일치해야 합니다.</div>
                        </div>
                        
                        <div class="form-group">
                            <label for="contactEmail">담당자 이메일 <span class="required">*</span></label>
                            <input type="email" id="contactEmail" name="contact_email" required 
                                   placeholder="developer@mybank.com">
                            <div class="help-text">승인 결과를 받을 담당자 이메일입니다.</div>
                        </div>
                        
                        <div class="form-group">
                            <label for="description">서비스 설명</label>
                            <textarea id="description" name="description" rows="4" 
                                      placeholder="서비스에 대한 간단한 설명을 입력하세요."></textarea>
                        </div>
                        
                        <button type="submit" class="register-btn" id="submitBtn">
                            📝 클라이언트 등록 신청
                        </button>
                    </form>
                    
                    <div class="info-box">
                        <div class="info-title">⚠️ 주의사항</div>
                        <p>• Redirect URI는 반드시 HTTPS를 사용해야 합니다. (개발 시에만 HTTP 허용)</p>
                        <p>• 서비스 도메인과 Redirect URI가 일치해야 합니다.</p>
                        <p>• 등록 후 수정은 별도 문의가 필요합니다.</p>
                    </div>
                </div>
                
                <script>
                    document.getElementById('clientForm').addEventListener('submit', function() {
                        const submitBtn = document.getElementById('submitBtn');
                        submitBtn.disabled = true;
                        submitBtn.textContent = '⏳ 등록 중...';
                    });
                    
                    // 도메인과 redirect URI 검증
                    document.getElementById('redirectUri').addEventListener('blur', function() {
                        const domain = document.getElementById('serviceDomain').value;
                        const redirectUri = this.value;
                        
                        if (domain && redirectUri && !redirectUri.includes(domain)) {
                            alert('⚠️ Redirect URI에 서비스 도메인이 포함되어야 합니다.');
                            this.focus();
                        }
                    });
                </script>
            </body>
            </html>
            """;
    }
    
    private String generateClientId() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        
        // 영문 대소문자 + 숫자 조합
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        
        // 접두사 추가
        sb.append("kftc_");
        
        // 24자리 랜덤 문자열 생성
        for (int i = 0; i < 24; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    private String generateClientSecret() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        
        // 영문 대소문자 + 숫자 + 특수문자 조합 (안전한 특수문자만)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        
        // 48자리 랜덤 문자열 생성
        for (int i = 0; i < 48; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
} 