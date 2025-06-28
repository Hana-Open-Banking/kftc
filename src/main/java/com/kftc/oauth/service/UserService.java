package com.kftc.oauth.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.oauth.domain.User;
import com.kftc.oauth.dto.UserInfoResponse;
import com.kftc.oauth.repository.OAuthTokenRepository;
import com.kftc.oauth.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    
    private final OAuthTokenRepository tokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserManagementService userManagementService;
    
    /**
     * 사용자 정보 조회
     */
    public UserInfoResponse getUserInfo(String accessToken, String userSeqNo) {
        log.info("사용자 정보 조회 시작: userSeqNo={}, accessToken={}", userSeqNo, accessToken.substring(0, 10) + "...");
        
        // 토큰 유효성 검증
        if (!jwtTokenProvider.validateToken(accessToken)) {
            log.error("JWT 토큰 검증 실패: {}", accessToken.substring(0, 10) + "...");
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 액세스 토큰입니다.");
        }
        log.info("JWT 토큰 검증 성공");
        
        // DB에서 토큰 상태 확인
        tokenRepository.findByAccessTokenAndIsRevokedFalse(accessToken)
                .orElseThrow(() -> {
                    log.error("DB에서 토큰을 찾을 수 없음: {}", accessToken.substring(0, 10) + "...");
                    return new BusinessException(ErrorCode.INVALID_TOKEN, "토큰을 찾을 수 없습니다.");
                });
        log.info("DB 토큰 검증 성공");
        
        // JWT에서 사용자 정보 추출
        String tokenUserId = jwtTokenProvider.getUserId(accessToken);
        String scope = jwtTokenProvider.getScope(accessToken);
        log.info("JWT에서 추출한 정보: userId={}, scope={}", tokenUserId, scope);
        
        // scope 검증 (login 스코프 필요)
        if (scope == null || !scope.contains("login")) {
            log.error("scope 검증 실패: scope={}", scope);
            throw new BusinessException(ErrorCode.INVALID_VALUE, "사용자 정보 조회 권한이 없습니다. login 스코프가 필요합니다.");
        }
        
        log.info("사용자 정보 조회 요청: userSeqNo={}, tokenUserId={}", userSeqNo, tokenUserId);
        
        // 실제 사용자 정보 조회
        User user = userManagementService.getActiveUser(userSeqNo);
        log.info("DB에서 사용자 정보 조회 성공: userSeqNo={}, userName={}", userSeqNo, user.getUserName());
        
        // 현재 시간 생성
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String apiTranId = generateApiTranId();
        
        // 사용자 정보 생성 (실제 DB 데이터 사용)
        UserInfoResponse.UserInfo userInfo = UserInfoResponse.UserInfo.builder()
                .userSeqNo(user.getUserSeqNum())
                .userCi(user.getUserCi())
                .userName(user.getUserName())
                .userCellNo(user.getUserCellNo())
                .userEmail(user.getUserEmail())
                .userBirthDate(user.getUserBirthDate())
                .userSexType(user.getUserSexType() != null ? user.getUserSexType().name() : null)
                .userType(user.getUserType() == User.UserType.PERSONAL ? "1" : "2")
                .joinDate(user.getJoinDate())
                .build();
        
        return UserInfoResponse.builder()
                .apiTranId(apiTranId)
                .apiTranDtm(currentDateTime)
                .rspCode("A0000")
                .rspMessage("정상처리")
                .userSeqNo(user.getUserSeqNum())
                .userCi(user.getUserCi())
                .userName(user.getUserName())
                .resCnt("1")
                .userInfo(userInfo)
                .build();
    }
    
    private String generateApiTranId() {
        // API 거래고유번호 생성 (오픈뱅킹 표준: M + 요청기관코드 + 14자리 타임스탬프 + 9자리 일련번호)
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long nanoTime = System.nanoTime();
        String sequence = String.format("%09d", Math.abs(nanoTime % 1000000000L));
        
        // M + 기관코드(8자리) + 타임스탬프(14자리) + 일련번호(9자리) = 총 32자리
        return "M" + "KFTC0001" + currentDateTime + sequence;
    }
} 