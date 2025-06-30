package com.kftc.oauth.controller;

import com.kftc.common.dto.BasicResponse;
import com.kftc.oauth.dto.AuthorizeRequest;

import com.kftc.oauth.dto.TokenRequest;
import com.kftc.oauth.dto.TokenResponse;
import com.kftc.oauth.service.OAuthService;
import com.kftc.user.dto.UserRegisterResponse;
import com.kftc.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/oauth/2.0")
@RequiredArgsConstructor
@Tag(name = "OAuth 2.0", description = "오픈뱅킹 OAuth 2.0 인증 API")
public class OAuthController {
    
    private final OAuthService oAuthService;
    private final UserService userService;
    
    @Operation(summary = "오픈뱅킹 인증 API", description = "오픈뱅킹 명세서에 따른 OAuth 2.0 인증 코드 발급 API")
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            // 필수 파라미터 (Y)
            @Parameter(description = "OAuth 2.0 인증 요청 시 반환되는 형태", required = true) 
            @RequestParam("response_type") String responseType,
            
            @Parameter(description = "오픈뱅킹에서 발급한 이용기관 앱의 Client ID", required = true) 
            @RequestParam("client_id") String clientId,
            
            @Parameter(description = "사용자인증이 성공하면 이용기관으로 연결되는 URL", required = true) 
            @RequestParam("redirect_uri") String redirectUri,
            
            @Parameter(description = "Access Token 권한 범위 (다중 scope 가능)", required = true) 
            @RequestParam("scope") String scope,
            
            @Parameter(description = "CSRF 보안위험에 대응하기 위해 이용기관이 세팅하는 난수값", required = true) 
            @RequestParam("state") String state,
            
            @Parameter(description = "사용자인증타입 구분 (0:최초인증, 1:재인증, 2:인증생략)", required = true) 
            @RequestParam("auth_type") String authType,
            
            // auth_type=2일 때만 필수인 헤더들을 선택적으로 변경
            @Parameter(description = "기존 고객의 사용자일련번호 (auth_type=2일 때 필수)", required = false) 
            @RequestHeader(value = "Kftc-Bfop-UserSeqNo", required = false) String userSeqNo,
            
            @Parameter(description = "사용자 CI(Connect Info) (auth_type=2일 때 필수)", required = false) 
            @RequestHeader(value = "Kftc-Bfop-UserCi", required = false) String userCi,
            
            @Parameter(description = "login scope를 포함한 토큰 (auth_type=2일 때 필수)", required = false) 
            @RequestHeader(value = "Kftc-Bfop-AccessToken", required = false) String accessToken,
            
            // 선택 파라미터 (N) - 필요한 것만 구현
            @Parameter(description = "요청 시 이용기관이 세팅한 임의의 정보", required = false) 
            @RequestParam(value = "client_info", required = false) String clientInfo) {
        
        // 필수 파라미터 검증
        if (!"code".equals(responseType)) {
            throw new IllegalArgumentException("response_type은 'code'여야 합니다.");
        }
        
        if (!authType.matches("[012]")) {
            throw new IllegalArgumentException("auth_type은 '0', '1', '2' 중 하나여야 합니다.");
        }
        
        // scope 검증 (다중 scope 지원)
        validateScopeFormat(scope);
        
        // 서비스등록확인 처리
        String authorizationCode = oAuthService.processAuthorizeAccount(
                clientId, redirectUri, scope, clientInfo, state, authType, 
                userSeqNo, userCi, accessToken);
        
        // Callback URL로 리다이렉트
        String redirectUrl = buildAuthorizeRedirectUrl(redirectUri, authorizationCode, scope, clientInfo, state);
        
        log.info("오픈뱅킹 인증 완료, 리다이렉트: {}", redirectUrl);
        
        return ResponseEntity.status(302)
                .location(URI.create(redirectUrl))
                .build();
    }
    
    @Operation(summary = "사용자 토큰발급 API (3-legged)", 
               description = "센터인증 이용기관이 사용자인증 API를 통하여 Authorization Code를 획득(사용자의 동의를 받았다는 의미한 이후에 이 code값을 이용하여 토큰을 발급받는 API입니다.")
    @PostMapping(value = "/token", produces = "application/json")
    public ResponseEntity<TokenResponse> token(
            @Parameter(description = "사용자인증 성공 후 획득한 Authorization Code", required = true) 
            @RequestParam("code") String code,
            
            @Parameter(description = "오픈뱅킹에서 발급한 이용기관 앱의 Client ID", required = true) 
            @RequestParam("client_id") String clientId,
            
            @Parameter(description = "오픈뱅킹에서 발급한 이용기관 앱의 Client Secret", required = true) 
            @RequestParam("client_secret") String clientSecret,
            
            @Parameter(description = "Access Token을 전달받을 Callback URL", required = true) 
            @RequestParam("redirect_uri") String redirectUri,
            
            @Parameter(description = "3-legged 인증을 위한 권한부여 방식 지정 (고정값: authorization_code)", required = true) 
            @RequestParam("grant_type") String grantType) {
        
        // grant_type 검증
        if (!"authorization_code".equals(grantType)) {
            throw new IllegalArgumentException("grant_type은 'authorization_code'여야 합니다.");
        }
        
        TokenRequest request = new TokenRequest();
        request.setClientId(clientId);
        request.setClientSecret(clientSecret);
        request.setGrantType(grantType);
        request.setCode(code);
        request.setRedirectUri(redirectUri);
        
        TokenResponse tokenResponse = oAuthService.issueTokenByAuthCode(request);
        
        return ResponseEntity.ok(tokenResponse);
    }
    
    @Operation(summary = "토큰 검증", description = "Access Token의 유효성을 검증합니다.")
    @PostMapping("/introspect")
    public ResponseEntity<BasicResponse> introspect(
            @Parameter(description = "Bearer 토큰") @RequestHeader("Authorization") String authorization) {
        
        if (!authorization.startsWith("Bearer ")) {
            BasicResponse errorResponse = BasicResponse.builder()
                    .status(400)
                    .message("유효하지 않은 Authorization 헤더입니다.")
                    .data(null)
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        String accessToken = authorization.substring(7);
        boolean isValid = oAuthService.validateAccessToken(accessToken);
        
        BasicResponse successResponse = BasicResponse.builder()
                .status(200)
                .message("성공")
                .data(isValid)
                .build();
        return ResponseEntity.ok(successResponse);
    }
    


    @Operation(summary = "서비스등록확인 API (3-legged)", 
               description = "센터인증 이용기관이 본인인증(SMS, 금융, 공동 인증 중 택1)을 통해 이미 등록된 서비스 대역을 확인하고 사용자의 계좌목록, 금융, 공동 인증 중 택1을 통해 금융정보 제공에 대한 동의를 받아 해당하는 API입니다.")
    @GetMapping("/authorize_account")
    public ResponseEntity<Void> authorizeAccount(
            @Parameter(description = "OAuth 2.0 인증 요청 시 반환되는 형태", required = true) 
            @RequestParam("response_type") String responseType,
            
            @Parameter(description = "오픈뱅킹에서 발급한 이용기관 앱의 Client ID", required = true) 
            @RequestParam("client_id") String clientId,
            
            @Parameter(description = "사용자인증이 성공하면 이용기관으로 연결되는 URL", required = true) 
            @RequestParam("redirect_uri") String redirectUri,
            
            @Parameter(description = "Access Token 권한 범위 (다중 scope 가능)", required = true) 
            @RequestParam("scope") String scope,
            
            @Parameter(description = "요청 시 이용기관이 세팅한 임의의 정보 (Callback 호출 시 그대로 전달)", required = false) 
            @RequestParam(value = "client_info", required = false) String clientInfo,
            
            @Parameter(description = "요청 시 이용기관이 세팅한 state 값을 그대로 전달", required = true) 
            @RequestParam(value = "state") String state,
            
            @Parameter(description = "사용자인증타입 구분 (0:본인인증필수, 2:본인인증생략)", required = true) 
            @RequestParam("auth_type") String authType,
            
            @Parameter(description = "다국어 설정", required = false) 
            @RequestParam(value = "lang", required = false) String lang,
            
            @Parameter(description = "휴대전화 인증 사용여부", required = false) 
            @RequestParam(value = "cellphone_cert_yn", required = false) String cellphoneCertYn,
            
            @Parameter(description = "공동·금융인증서 사용여부", required = false) 
            @RequestParam(value = "authorized_cert_yn", required = false) String authorizedCertYn,
            
            @Parameter(description = "등록정보 종류", required = false) 
            @RequestParam(value = "register_info", required = false) String registerInfo,
            
            @Parameter(description = "기존 고객의 사용자일련번호", required = false) 
            @RequestHeader(value = "Kftc-Bfop-UserSeqNo", required = false) String userSeqNo,
            
            @Parameter(description = "사용자 CI(Connect Info)", required = false) 
            @RequestHeader(value = "Kftc-Bfop-UserCi", required = false) String userCi,
            
            @Parameter(description = "login scope를 포함한 토큰 (유효한 접속토큰이어야 함)", required = false) 
            @RequestHeader(value = "Kftc-Bfop-AccessToken", required = false) String accessToken) {
        
        // 파라미터 검증
        if (!"code".equals(responseType)) {
            throw new IllegalArgumentException("response_type은 'code'여야 합니다.");
        }
        
        if (!"0".equals(authType) && !"2".equals(authType)) {
            throw new IllegalArgumentException("auth_type은 '0' 또는 '2'여야 합니다.");
        }
        
        // 서비스등록확인 처리
        String authorizationCode = oAuthService.processAuthorizeAccount(
                clientId, redirectUri, scope, clientInfo, state, authType, 
                userSeqNo, userCi, accessToken);
        
        // Callback URL로 리다이렉트
        String redirectUrl = buildRedirectUrl(redirectUri, authorizationCode, scope, clientInfo, state);
        
        log.info("서비스등록확인 완료, 리다이렉트: {}", redirectUrl);
        
        return ResponseEntity.status(302)
                .location(URI.create(redirectUrl))
                .build();
    }
    
    private void validateScopeFormat(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            throw new IllegalArgumentException("scope는 필수입니다.");
        }
        
        // 다중 scope 검증 - sa 스코프 추가
        String[] scopes = scope.split("\\|");
        for (String singleScope : scopes) {
            if (!singleScope.trim().matches("sa|login|inquiry|transfer|cardinfo|fintechinfo|insurinfo|loaninfo")) {
                throw new IllegalArgumentException("유효하지 않은 scope입니다: " + singleScope);
            }
        }
    }
    
    private String buildAuthorizeRedirectUrl(String redirectUri, String code, String scope, String clientInfo, String state) {
        try {
            StringBuilder url = new StringBuilder(redirectUri);
            url.append("?code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
            url.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
            
            if (clientInfo != null && !clientInfo.trim().isEmpty()) {
                url.append("&client_info=").append(URLEncoder.encode(clientInfo, StandardCharsets.UTF_8));
            }
            
            if (state != null && !state.trim().isEmpty()) {
                url.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
            }
            
            return url.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("URL 생성 중 오류가 발생했습니다.", e);
        }
    }
    
    private String buildRedirectUrl(String redirectUri, String code, String scope, String clientInfo, String state) {
        try {
            StringBuilder url = new StringBuilder(redirectUri);
            url.append("?code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
            url.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
            
            if (clientInfo != null) {
                url.append("&client_info=").append(URLEncoder.encode(clientInfo, StandardCharsets.UTF_8));
            }
            
            if (state != null) {
                url.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
            }
            
            return url.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("URL 생성 중 오류가 발생했습니다.", e);
        }
    }
    
    @Operation(summary = "OAuth Callback", description = "OAuth 인증 완료 후 리다이렉트되는 엔드포인트")
    @GetMapping("/callback")
    public ResponseEntity<String> oauthCallback(
            @Parameter(description = "Authorization Code", required = true) 
            @RequestParam("code") String code,
            
            @Parameter(description = "Scope", required = true) 
            @RequestParam("scope") String scope,
            
            @Parameter(description = "State", required = true) 
            @RequestParam("state") String state,
            
            @Parameter(description = "Client Info", required = false) 
            @RequestParam(value = "client_info", required = false) String clientInfo) {
        
        log.info("OAuth Callback 호출: code={}, scope={}, state={}", 
                code.substring(0, Math.min(10, code.length())) + "...", scope, state);
        
        // 사용자 콜백 처리 - state가 숫자인 경우 사용자 ID로 간주
        try {
            Long.parseLong(state);
            // 사용자 회원가입 관련 콜백 처리
            UserRegisterResponse userResponse = userService.handleKftcCallback(code, state);
            
            String userHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>KFTC 연동 완료</title>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        margin: 0; padding: 20px; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh; display: flex; align-items: center; justify-content: center;
                    }
                    .container { 
                        max-width: 600px; background: white; padding: 40px; border-radius: 16px; 
                        box-shadow: 0 10px 30px rgba(0,0,0,0.2); position: relative; overflow: hidden;
                    }
                    .success { 
                        color: #28a745; margin-bottom: 20px; display: flex; align-items: center; 
                        font-size: 1.8em; font-weight: bold;
                    }
                    .success::before { content: '🎉'; margin-right: 10px; font-size: 1.2em; }
                    .user-info { 
                        background: #f8f9fa; padding: 20px; border-radius: 8px; 
                        margin: 20px 0; border-left: 4px solid #28a745;
                    }
                    .info-item { margin: 10px 0; }
                    .info-label { font-weight: bold; color: #495057; }
                    .info-value { color: #6c757d; margin-left: 10px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1 class="success">KFTC 연동 완료!</h1>
                    <p>KFTC 오픈뱅킹 연동이 성공적으로 완료되었습니다.</p>
                    
                    <div class="user-info">
                        <strong>👤 사용자 정보:</strong><br>
                        <div class="info-item">
                            <span class="info-label">이름:</span>
                            <span class="info-value">%s</span>
                        </div>
                        <div class="info-item">
                            <span class="info-label">휴대폰번호:</span>
                            <span class="info-value">%s</span>
                        </div>
                        <div class="info-item">
                            <span class="info-label">사용자일련번호:</span>
                            <span class="info-value">%s</span>
                        </div>
                        <div class="info-item">
                            <span class="info-label">Access Token:</span>
                            <span class="info-value">%s...</span>
                        </div>
                    </div>
                    
                    <p><strong>이제 KFTC 오픈뱅킹 API를 사용할 수 있습니다!</strong></p>
                </div>
            </body>
            </html>
            """.formatted(
                userResponse.getName(),
                userResponse.getPhoneNumber(),
                userResponse.getUserSeqNo(),
                userResponse.getAccessToken() != null ? 
                    userResponse.getAccessToken().substring(0, Math.min(20, userResponse.getAccessToken().length())) : "N/A"
            );
            
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(userHtml);
                    
        } catch (NumberFormatException e) {
            // 숫자가 아닌 경우 기존 OAuth 콜백 처리
        }
        
        String html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>OAuth 인증 완료</title>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { 
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    margin: 0; padding: 20px; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                    min-height: 100vh; display: flex; align-items: center; justify-content: center;
                }
                .container { 
                    max-width: 600px; background: white; padding: 40px; border-radius: 16px; 
                    box-shadow: 0 10px 30px rgba(0,0,0,0.2); position: relative; overflow: hidden;
                }
                .success { 
                    color: #28a745; margin-bottom: 20px; display: flex; align-items: center; 
                    font-size: 1.8em; font-weight: bold;
                }
                .success::before { content: '🎉'; margin-right: 10px; font-size: 1.2em; }
                .code-box { 
                    background: #f8f9fa; padding: 20px; border-radius: 8px; 
                    margin: 20px 0; border-left: 4px solid #007bff; position: relative;
                }
                .code-box::before {
                    content: '📋'; position: absolute; top: 20px; right: 20px; 
                    font-size: 1.2em; opacity: 0.5;
                }
                .next-step { 
                    background: linear-gradient(135deg, #e7f3ff 0%%, #f0f8ff 100%%); 
                    padding: 20px; border-radius: 8px; margin: 20px 0; 
                    border-left: 4px solid #0066cc; position: relative;
                }
                .next-step::before {
                    content: '🚀'; position: absolute; top: 20px; right: 20px; 
                    font-size: 1.2em; opacity: 0.7;
                }
                code { 
                    font-family: 'Monaco', 'Consolas', monospace; 
                    word-break: break-all; background: #e9ecef; padding: 2px 6px; 
                    border-radius: 4px; font-size: 0.9em;
                }
                .copy-btn {
                    background: #007bff; color: white; border: none; padding: 8px 16px;
                    border-radius: 4px; cursor: pointer; margin-top: 10px; 
                    font-size: 0.9em; transition: background 0.3s;
                }
                .copy-btn:hover { background: #0056b3; }
                .swagger-link {
                    display: inline-block; background: #28a745; color: white; 
                    text-decoration: none; padding: 12px 24px; border-radius: 6px; 
                    margin-top: 20px; font-weight: bold; transition: background 0.3s;
                }
                .swagger-link:hover { background: #218838; }
                .info-grid {
                    display: grid; grid-template-columns: 1fr 1fr; gap: 15px; 
                    margin: 20px 0;
                }
                .info-item {
                    background: #f8f9fa; padding: 15px; border-radius: 6px;
                    border-left: 3px solid #6c757d;
                }
                .info-label { font-weight: bold; color: #495057; margin-bottom: 5px; }
                .info-value { color: #6c757d; font-family: monospace; font-size: 0.9em; }
            </style>
        </head>
        <body>
            <div class="container">
                <h1 class="success">OAuth 인증 성공!</h1>
                <p>Authorization Code가 성공적으로 발급되었습니다. 이제 토큰 발급 단계로 진행하세요.</p>
                
                <div class="code-box">
                    <strong>📋 Authorization Code:</strong><br>
                    <code id="authCode">%s</code>
                    <button class="copy-btn" onclick="copyToClipboard('authCode')">복사</button>
                </div>
                
                <div class="info-grid">
                    <div class="info-item">
                        <div class="info-label">Scope</div>
                        <div class="info-value">%s</div>
                    </div>
                    <div class="info-item">
                        <div class="info-label">State</div>
                        <div class="info-value">%s</div>
                    </div>
                </div>
                
                %s
                
                <div class="next-step">
                    <strong>🚀 다음 단계:</strong><br>
                    <ol>
                        <li>위의 Authorization Code를 복사하세요</li>
                        <li>아래 Swagger UI 링크를 클릭하세요</li>
                        <li><code>POST /oauth2.0/token</code> API를 찾으세요</li>
                        <li>다음 값들을 입력하세요:
                            <ul>
                                <li><strong>code:</strong> 위에서 복사한 Authorization Code</li>
                                <li><strong>client_id:</strong> kftc-openbanking-client</li>
                                <li><strong>client_secret:</strong> kftc-openbanking-secret</li>
                                <li><strong>redirect_uri:</strong> http://localhost:8080/oauth2/callback</li>
                                <li><strong>grant_type:</strong> authorization_code</li>
                            </ul>
                        </li>
                        <li>Execute를 클릭해서 Access Token을 받으세요</li>
                    </ol>
                </div>
                
                <a href="/swagger-ui.html" target="_blank" class="swagger-link">🔗 Swagger UI로 이동</a>
            </div>
            
            <script>
                function copyToClipboard(elementId) {
                    const element = document.getElementById(elementId);
                    const text = element.textContent;
                    navigator.clipboard.writeText(text).then(function() {
                        alert('Authorization Code가 클립보드에 복사되었습니다!');
                    }, function(err) {
                        console.error('복사 실패: ', err);
                    });
                }
            </script>
        </body>
        </html>
        """.formatted(
            code, 
            scope, 
            state,
            clientInfo != null ? String.format("""
                <div class="info-item" style="grid-column: 1 / -1;">
                    <div class="info-label">Client Info</div>
                    <div class="info-value">%s</div>
                </div>
                """, clientInfo) : ""
        );
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(html);
    }
} 