package com.kftc.oauth.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
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
public class OAuthUserService {
    
    private final OAuthTokenRepository tokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    
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
        
        // 현재 시간 생성
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String apiTranId = generateApiTranId();
        
        // 사용자 정보 생성 (실제로는 사용자 DB에서 조회해야 함)
        UserInfoResponse.UserInfo userInfo = UserInfoResponse.UserInfo.builder()
                .userSeqNo(userSeqNo)
                .userCi("s1V7bwE4pxqV_K5oy4EdEOGUHUIpv7_2l4kE8l7FOC4HCi-7TUtT9-jaVL9kEj4GB12eKIkfmL49OCtGwI12-C")
                .userName("김철수")
                .userCellNo("01012345678")
                .userEmail("test@openbanking.or.kr")
                .userBirthDate("19880101")
                .userSexType("M")
                .userType("1")
                .joinDate("20190101")
                .build();
        
        return UserInfoResponse.builder()
                .apiTranId(apiTranId)
                .apiTranDtm(currentDateTime)
                .rspCode("A0000")
                .rspMessage("정상처리")
                .userSeqNo(userSeqNo)
                .userCi("s1V7bwE4pxqV_K5oy4EdEOGUHUIpv7_2l4kE8l7FOC4HCi-7TUtT9-jaVL9kEj4GB12eKIkfmL49OCtGwI12-C")
                .userName("김철수")
                .resCnt("1")
                .userInfo(userInfo)
                .build();
    }
    
    private String generateApiTranId() {
        // API 거래고유번호 생성 (실제로는 UUID 등 사용)
        return "M202301234567890123456789012345";
    }
} 