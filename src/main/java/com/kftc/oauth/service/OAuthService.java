package com.kftc.oauth.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.oauth.domain.AuthorizationCode;
import com.kftc.oauth.domain.OAuthClient;
import com.kftc.oauth.domain.OAuthToken;

import com.kftc.oauth.dto.AuthorizeRequest;

import com.kftc.oauth.dto.TokenRequest;
import com.kftc.oauth.dto.TokenResponse;
import com.kftc.oauth.repository.AuthorizationCodeRepository;
import com.kftc.oauth.repository.OAuthClientRepository;
import com.kftc.oauth.repository.OAuthTokenRepository;
import com.kftc.oauth.util.JwtTokenProvider;
import com.kftc.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OAuthService {
    
    private final OAuthClientRepository clientRepository;
    private final AuthorizationCodeRepository codeRepository;
    private final OAuthTokenRepository tokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    
    @Value("${oauth.token.access-token-validity}")
    private long accessTokenValidityInSeconds;
    
    @Value("${oauth.token.refresh-token-validity}")
    private long refreshTokenValidityInSeconds;
    
    /**
     * OAuth 인증 코드 발급
     */
    public String generateAuthorizationCode(AuthorizeRequest request) {
        // 클라이언트 검증
        OAuthClient client = validateClient(request.getClientId());
        
        // Redirect URI 검증 (대소문자 무시, 공백 제거)
        String dbUri = client.getRedirectUri().trim().toLowerCase();
        String requestUri = request.getRedirectUri().trim().toLowerCase();
        
        if (!dbUri.equals(requestUri)) {
            log.error("일반 인증 Redirect URI 불일치! DB:[{}] vs 요청:[{}]", dbUri, requestUri);
            throw new BusinessException(ErrorCode.INVALID_VALUE, "유효하지 않은 Redirect URI입니다.");
        }
        
        // 기존 코드 삭제 (있다면)
        codeRepository.deleteByClientIdAndUserId(request.getClientId(), request.getUserId());
        
        // 새 인증 코드 생성
        String code = generateSecureCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10); // 10분 유효
        
        AuthorizationCode authCode = AuthorizationCode.builder()
                .code(code)
                .clientId(request.getClientId())
                .userId(request.getUserId())
                .redirectUri(request.getRedirectUri())
                .scope(request.getScope())
                .expiresAt(expiresAt)
                .build();
        
        codeRepository.save(authCode);
        
        log.info("인증 코드 발급 완료: clientId={}, userId={}", request.getClientId(), request.getUserId());
        return code;
    }
    
    /**
     * 액세스 토큰 발급 (Authorization Code Grant)
     */
    public TokenResponse issueTokenByAuthCode(TokenRequest request) {
        log.info("=== 토큰 발급 시작 ===");
        log.info("요청 코드: {}", request.getCode());
        log.info("요청 클라이언트 ID: {}", request.getClientId());
        log.info("요청 리다이렉트 URI: {}", request.getRedirectUri());
        
        // 클라이언트 인증
        OAuthClient client = authenticateClient(request.getClientId(), request.getClientSecret());
        log.info("클라이언트 인증 성공: {}", client.getClientId());
        
        // 인증 코드 검증
        AuthorizationCode authCode = codeRepository.findByCodeAndIsUsedFalse(request.getCode())
                .orElseThrow(() -> {
                    log.error("인증 코드를 찾을 수 없거나 이미 사용됨: {}", request.getCode());
                    return new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "유효하지 않은 인증 코드입니다.");
                });
        
        log.info("=== 인증 코드 정보 ===");
        log.info("DB 저장 코드: {}", authCode.getCode());
        log.info("DB 저장 클라이언트 ID: {}", authCode.getClientId());
        log.info("DB 저장 리다이렉트 URI: {}", authCode.getRedirectUri());
        log.info("DB 저장 사용자 ID (userId): [{}]", authCode.getUserId());
        log.info("사용자 ID 타입: {}", authCode.getUserId() != null ? authCode.getUserId().getClass().getSimpleName() : "null");
        log.info("사용자 ID 길이: {}", authCode.getUserId() != null ? authCode.getUserId().length() : 0);
        log.info("DB 저장 만료시간: {}", authCode.getExpiresAt());
        log.info("현재 시간: {}", java.time.LocalDateTime.now());
        log.info("만료 여부: {}", authCode.isExpired());
        
        if (authCode.isExpired()) {
            log.error("인증 코드가 만료됨: {}", authCode.getExpiresAt());
            throw new BusinessException(ErrorCode.INVALID_VALUE, "만료된 인증 코드입니다.");
        }
        
        log.info("=== 코드 정보 일치 검증 ===");
        log.info("클라이언트 ID 일치: {} vs {} = {}", 
                authCode.getClientId(), request.getClientId(), 
                authCode.getClientId().equals(request.getClientId()));
        log.info("리다이렉트 URI 일치: {} vs {} = {}", 
                authCode.getRedirectUri(), request.getRedirectUri(), 
                authCode.getRedirectUri().equals(request.getRedirectUri()));
        
        if (!authCode.getClientId().equals(request.getClientId()) ||
            !authCode.getRedirectUri().equals(request.getRedirectUri())) {
            log.error("인증 코드 정보 불일치!");
            log.error("클라이언트 ID - DB: [{}], 요청: [{}]", authCode.getClientId(), request.getClientId());
            log.error("리다이렉트 URI - DB: [{}], 요청: [{}]", authCode.getRedirectUri(), request.getRedirectUri());
            throw new BusinessException(ErrorCode.INVALID_VALUE, "해당 코드 정보가 일치하지 않습니다.");
        }
        
        // 코드 사용 처리
        authCode.markAsUsed();
        
        // 기존 토큰 무효화
        revokeTokensByClientAndUser(request.getClientId(), authCode.getUserId());
        
        // 새 토큰 생성
        return generateTokenResponse(client, authCode.getUserId(), authCode.getScope());
    }
    
    /**
     * 액세스 토큰 갱신 (Refresh Token Grant)
     */
    public TokenResponse refreshToken(TokenRequest request) {
        // 클라이언트 인증
        OAuthClient client = authenticateClient(request.getClientId(), request.getClientSecret());
        
        // Refresh Token 검증
        OAuthToken existingToken = tokenRepository.findByRefreshTokenAndIsRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "유효하지 않은 Refresh Token입니다."));
        
        if (existingToken.isRefreshTokenExpired()) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "만료된 Refresh Token입니다.");
        }
        
        if (!existingToken.getClientId().equals(request.getClientId())) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "Refresh Token의 클라이언트 ID가 일치하지 않습니다.");
        }
        
        // 기존 토큰 무효화
        existingToken.revoke();
        
        // 새 토큰 생성
        return generateTokenResponse(client, existingToken.getUserId(), existingToken.getScope());
    }
    
    /**
     * 액세스 토큰 발급 (Client Credentials Grant)
     */
    public TokenResponse issueTokenByClientCredentials(TokenRequest request) {
        // 클라이언트 인증
        OAuthClient client = authenticateClient(request.getClientId(), request.getClientSecret());
        
        // scope 검증 (오픈뱅킹에서는 oob 고정)
        String scope = request.getScope();
        if (scope == null || !scope.equals("oob")) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "유효하지 않은 scope입니다. scope는 'oob'이어야 합니다.");
        }
        
        // 기존 토큰 무효화 (Client Credentials에서는 사용자가 없으므로 클라이언트 기준)
        tokenRepository.findByClientIdAndIsRevokedFalse(request.getClientId())
                .forEach(OAuthToken::revoke);
        
        // 새 토큰 생성 (Client Credentials에서는 사용자 ID 없음)
        return generateClientCredentialsTokenResponse(client, scope);
    }
    
    /**
     * 토큰 검증
     */
    @Transactional(readOnly = true)
    public boolean validateAccessToken(String accessToken) {
        try {
            // JWT 검증
            if (!jwtTokenProvider.validateToken(accessToken)) {
                return false;
            }
            
            // DB에서 토큰 상태 확인
            OAuthToken token = tokenRepository.findByAccessTokenAndIsRevokedFalse(accessToken)
                    .orElse(null);
            
            return token != null && !token.isAccessTokenExpired();
        } catch (Exception e) {
            log.error("토큰 검증 중 오류 발생: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 토큰 무효화
     */
    public void revokeToken(String accessToken) {
        tokenRepository.findByAccessToken(accessToken)
                .ifPresent(OAuthToken::revoke);
        log.info("토큰이 무효화되었습니다: {}", accessToken.substring(0, 10) + "...");
    }
    

    
    /**
     * 서비스등록확인 API (3-legged) 처리
     */
    public String processAuthorizeAccount(String clientId, String redirectUri, String scope, 
                                        String clientInfo, String state, String authType,
                                        String userSeqNo, String userCi, String accessToken) {
        
        // 클라이언트 검증
        OAuthClient client = validateClient(clientId);
        
        // Redirect URI 검증 (대소문자 무시, 공백 제거)
        String dbUri = client.getRedirectUri().trim().toLowerCase();
        String requestUri = redirectUri.trim().toLowerCase();
        
        log.info("=== Redirect URI 검증 ===");
        log.info("DB에 저장된 redirect_uri: [{}] -> 정규화: [{}]", client.getRedirectUri(), dbUri);
        log.info("요청받은 redirect_uri: [{}] -> 정규화: [{}]", redirectUri, requestUri);
        log.info("두 값이 같은가?: {}", dbUri.equals(requestUri));
        
        if (!dbUri.equals(requestUri)) {
            log.error("Redirect URI 불일치! DB:[{}] vs 요청:[{}]", dbUri, requestUri);
            throw new BusinessException(ErrorCode.INVALID_VALUE, "유효하지 않은 Redirect URI입니다.");
        }
        
        // scope 검증 (다중 scope 지원)
        validateScope(scope);
        
        // auth_type에 따른 처리
        String userId = processAuthType(authType, userSeqNo, userCi, accessToken);
        
        // 기존 코드 삭제 (있다면)
        codeRepository.deleteByClientIdAndUserId(clientId, userId);
        
        // 새 인증 코드 생성
        String code = generateSecureCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10); // 10분 유효
        
        AuthorizationCode authCode = AuthorizationCode.builder()
                .code(code)
                .clientId(clientId)
                .userId(userId)
                .redirectUri(redirectUri)
                .scope(scope)
                .expiresAt(expiresAt)
                .build();
        
        codeRepository.save(authCode);
        
        log.info("서비스등록확인 인증 코드 발급 완료: clientId={}, userId={}, authType={}", 
                clientId, userId, authType);
        
        return code;
    }
    
    /**
     * 클라이언트 검증 (public 메서드)
     */
    @Transactional(readOnly = true)
    public OAuthClient validateClient(String clientId) {
        return clientRepository.findByClientIdAndIsActiveTrue(clientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "유효하지 않은 클라이언트입니다."));
    }
    
    private OAuthClient authenticateClient(String clientId, String clientSecret) {
        OAuthClient client = validateClient(clientId);
        
        if (!passwordEncoder.matches(clientSecret, client.getClientSecret())) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "클라이언트 인증에 실패했습니다.");
        }
        
        return client;
    }
    
    private void revokeTokensByClientAndUser(String clientId, String userId) {
        tokenRepository.findByClientIdAndIsRevokedFalse(clientId)
                .stream()
                .filter(token -> token.getUserId().equals(userId))
                .forEach(OAuthToken::revoke);
    }
    
    private TokenResponse generateTokenResponse(OAuthClient client, String userId, String scope) {
        log.info("=== 토큰 응답 생성 시작 ===");
        log.info("입력 파라미터 - clientId: {}, userId: [{}], scope: {}", client.getClientId(), userId, scope);
        
        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(client.getClientId(), userId, scope);
        String refreshToken = jwtTokenProvider.generateRefreshToken(client.getClientId(), userId);
        log.info("JWT 토큰 생성 완료");
        
        // DB에 토큰 저장
        LocalDateTime now = LocalDateTime.now();
        OAuthToken token = OAuthToken.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .clientId(client.getClientId())
                .userId(userId)
                .scope(scope)
                .accessTokenExpiresAt(now.plusSeconds(accessTokenValidityInSeconds))
                .refreshTokenExpiresAt(now.plusSeconds(refreshTokenValidityInSeconds))
                .build();
        
        tokenRepository.save(token);
        log.info("DB에 토큰 저장 완료: clientId={}, userId=[{}]", client.getClientId(), userId);
        
        // 사용자 일련번호 생성 (실제로는 사용자 정보에서 가져와야 함)
        log.info("사용자 일련번호 생성 시작 - 입력 userId: [{}]", userId);
        String userSeqNo = generateUserSeqNo(userId);
        log.info("사용자 일련번호 생성 완료: {}", userSeqNo);
        
        TokenResponse response = TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenValidityInSeconds)
                .refreshToken(refreshToken)
                .scope(scope)
                .userSeqNo(userSeqNo)
                .build();
        
        log.info("=== 최종 토큰 응답 ===");
        log.info("userSeqNo: {}", response.getUserSeqNo());
        log.info("scope: {}", response.getScope());
        
        return response;
    }
    
    private String generateUserSeqNo(String userId) {
        log.info("=== generateUserSeqNo 시작 ===");
        log.info("입력 userId: [{}]", userId);
        
        // userId가 null이나 빈 문자열인 경우 기본값 반환
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("userId가 null이거나 빈 문자열입니다. 기본값 반환");
            return "1000000106";
        }
        
        try {
            // userId가 숫자 형태인 경우 (10자리든 아니든) userSeqNo로 간주하고 그대로 반환
            if (userId.matches("\\d+")) {
                log.info("숫자 형태 userId 감지: {} -> userSeqNo로 간주하고 그대로 반환", userId);
                
                // DB에서 해당 사용자가 존재하는지 확인 (선택사항)
                try {
                    Optional<com.kftc.user.entity.User> userOpt = userService.findActiveUserByUserSeqNo(userId);
                    if (userOpt.isPresent()) {
                        log.info("DB에서 사용자 확인됨: userSeqNo={}", userOpt.get().getUserSeqNo());
                        return userOpt.get().getUserSeqNo();
                    } else {
                        log.info("DB에서 사용자를 찾을 수 없지만 숫자 형태이므로 입력값 그대로 반환: {}", userId);
                        return userId; // DB에 없어도 숫자 형태면 그대로 반환
                    }
                } catch (Exception dbEx) {
                    log.warn("DB 조회 중 예외 발생하지만 숫자 형태이므로 입력값 그대로 반환: userId={}, error={}", userId, dbEx.getMessage());
                    return userId; // DB 조회 실패해도 숫자 형태면 그대로 반환
                }
            }
            
            // CI 형태인 경우 사용자 생성/조회
            if (userId.startsWith("temp_ci_")) {
                log.info("temp_ci_ 형식 userId 감지: {}", userId);
                String userSeqNo = userService.createOrGetUserByCi(userId);
                log.info("CI로 사용자 생성/조회 완료: userSeqNo={}", userSeqNo);
                return userSeqNo;
            }
            
            // 그 외의 경우 기본값 반환
            log.warn("예상하지 못한 userId 형태, 기본값 반환: userId=[{}]", userId);
            return "1000000106";
            
        } catch (Exception e) {
            log.error("사용자 조회/생성 중 예외 발생: userId={}, error={}", userId, e.getMessage(), e);
            
            // 예외 발생 시에도 숫자 형태면 그대로 반환
            if (userId.matches("\\d+")) {
                log.info("예외 발생했지만 숫자 형태이므로 입력값 그대로 반환: {}", userId);
                return userId;
            }
            
            // 그 외의 경우 기본값 반환
            return "1000000106";
        }
    }
    
    private String generateTempUserCi(String userId) {
        // 임시 CI 생성 (실제로는 본인인증을 통해 받아야 함)
        return "temp_ci_" + userId + "_" + System.currentTimeMillis();
    }
    
    private TokenResponse generateClientCredentialsTokenResponse(OAuthClient client, String scope) {
        // JWT 토큰 생성 (Client Credentials에서는 userId 없음)
        String accessToken = jwtTokenProvider.generateAccessToken(client.getClientId(), null, scope);
        
        // DB에 토큰 저장
        LocalDateTime now = LocalDateTime.now();
        OAuthToken token = OAuthToken.builder()
                .accessToken(accessToken)
                .refreshToken(null) // Client Credentials에서는 refresh token 없음
                .clientId(client.getClientId())
                .userId(null)
                .scope(scope)
                .accessTokenExpiresAt(now.plusSeconds(accessTokenValidityInSeconds))
                .refreshTokenExpiresAt(null)
                .build();
        
        tokenRepository.save(token);
        
        log.info("Client Credentials 토큰 발급 완료: clientId={}", client.getClientId());
        
        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenValidityInSeconds)
                .scope(scope)
                .userSeqNo("1000000106") // Client Credentials에서는 사용자 없음
                .build();
    }
    
    private void validateScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "scope는 필수입니다.");
        }
        
        // 다중 scope 검증 (login|inquiry|transfer|cardinfo|fintechinfo|insurinfo|loaninfo 등)
        String[] scopes = scope.split("\\|");
        for (String singleScope : scopes) {
            if (!isValidScope(singleScope.trim())) {
                throw new BusinessException(ErrorCode.INVALID_VALUE, "유효하지 않은 scope입니다: " + singleScope);
            }
        }
    }
    
    private boolean isValidScope(String scope) {
        return scope.matches("sa|login|inquiry|transfer|cardinfo|fintechinfo|insurinfo|loaninfo");
    }
    
    private String processAuthType(String authType, String userSeqNo, String userCi, String accessToken) {
        switch (authType) {
            case "0": // 최초인증 (본인인증필수)
                // 실제 구현에서는 본인인증 프로세스를 거쳐야 함
                log.info("최초인증 모드로 처리 (본인인증필수)");
                return generateUserId(); // 임시로 사용자 ID 생성
                
            case "1": // 재인증
                // 기존 사용자의 재인증 처리
                log.info("재인증 모드로 처리");
                if (userSeqNo != null && !userSeqNo.trim().isEmpty()) {
                    log.info("기존 사용자 일련번호로 재인증: {}", userSeqNo);
                    return userSeqNo;
                }
                return generateUserId();
                
            case "2": // 인증생략
                // 기존 사용자 정보 활용
                if (userSeqNo != null && !userSeqNo.trim().isEmpty()) {
                    log.info("기존 사용자 일련번호 사용: {}", userSeqNo);
                    return userSeqNo;
                }
                
                if (userCi != null && !userCi.trim().isEmpty()) {
                    log.info("사용자 CI 사용: {}", userCi.substring(0, Math.min(10, userCi.length())) + "...");
                    return userCi;
                }
                
                // AccessToken에서 사용자 정보 추출 (실제로는 JWT 파싱 필요)
                if (accessToken != null && !accessToken.trim().isEmpty()) {
                    log.info("AccessToken에서 사용자 정보 추출");
                    return extractUserFromToken(accessToken);
                }
                
                // 모든 정보가 없으면 임시 사용자 생성
                return generateUserId();
                
            default:
                throw new BusinessException(ErrorCode.INVALID_VALUE, "지원하지 않는 auth_type입니다: " + authType + " (0:최초인증, 1:재인증, 2:인증생략)");
        }
    }
    
    private String extractUserFromToken(String accessToken) {
        // 실제로는 JWT 토큰을 파싱해서 사용자 정보를 추출해야 함
        // 여기서는 임시로 토큰 기반 사용자 ID 생성
        return "token_user_" + Math.abs(accessToken.hashCode());
    }
    
    private String generateUserId() {
        return "user_" + System.currentTimeMillis();
    }
    
    private String generateSecureCode() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 클라이언트 ID로 클라이언트 정보 조회
     */
    @Transactional(readOnly = true)
    public OAuthClient getClientById(String clientId) {
        return clientRepository.findByClientIdAndIsActiveTrue(clientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "클라이언트를 찾을 수 없습니다: " + clientId));
    }
    
    /**
     * Authorization Code 생성 (동의 완료 후)
     */
    @Transactional
    public String generateAuthorizationCode(String clientId, String userSeqNo, String scope, String redirectUri) {
        log.info("=== Authorization Code 생성 시작 ===");
        log.info("clientId: {}, userSeqNo: {}, scope: {}, redirectUri: {}", clientId, userSeqNo, scope, redirectUri);
        
        // 클라이언트 검증
        OAuthClient client = getClientById(clientId);
        log.info("클라이언트 검증 완료: {}", client.getClientName());
        
        // 기존 코드 삭제 (있다면)
        codeRepository.deleteByClientIdAndUserId(clientId, userSeqNo);
        log.info("기존 Authorization Code 삭제 완료");
        
        // 새 인증 코드 생성
        String code = generateSecureCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10); // 10분 유효
        
        AuthorizationCode authCode = AuthorizationCode.builder()
                .code(code)
                .clientId(clientId)
                .userId(userSeqNo)  // 여기서 userSeqNo를 userId로 저장
                .redirectUri(redirectUri)
                .scope(scope)
                .expiresAt(expiresAt)
                .build();
        
        codeRepository.save(authCode);
        
        log.info("Authorization Code 발급 완료: code={}, DB에 저장된 userId={}", code, userSeqNo);
        
        return code;
    }
    
    /**
     * 사용자 인증 처리 (PASS 인증 결과 처리)
     */
    @Transactional
    public String processUserAuth(String userCi, String userName, String phoneNumber) {
        return processUserAuth(userCi, userName, phoneNumber, null);
    }
    
    /**
     * 사용자 인증 처리 (PASS 인증 결과 처리) - 이메일 포함
     */
    @Transactional
    public String processUserAuth(String userCi, String userName, String phoneNumber, String userEmail) {
        log.info("사용자 인증 처리: userCi={}, userName={}, userEmail={}", userCi, userName, userEmail);
        
        try {
            // UserService를 통해 사용자 생성 또는 조회 (이름, 이메일 포함)
            String userSeqNo = userService.createOrGetUserByCi(userCi, userName, userEmail);
            
            log.info("사용자 인증 처리 완료: userSeqNo={}", userSeqNo);
            return userSeqNo;
        } catch (Exception e) {
            log.warn("사용자 인증 처리 실패, 임시 사용자 생성: userCi={}", userCi, e);
            
            // 실패 시 CI를 기반으로 임시 사용자 ID 생성
            return "temp_user_" + Math.abs(userCi.hashCode());
        }
    }
} 