package com.kftc.user.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.user.dto.OpenBankingRegisterRequest;
import com.kftc.user.dto.OpenBankingRegisterResponse;
import com.kftc.user.dto.KftcTokenResponse;
import com.kftc.user.dto.UserRegisterResponse;
import com.kftc.user.entity.User;
import com.kftc.user.repository.UserRepository;
import com.kftc.user.service.KftcInternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OpenBankingService {
    
    private final UserRepository userRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final KftcInternalService kftcInternalService;
    
    /**
     * 오픈뱅킹 회원가입 처리
     */
    public OpenBankingRegisterResponse registerMember(OpenBankingRegisterRequest request) {
        log.info("=== 오픈뱅킹 회원가입 시작 ===");
        log.info("요청 정보: name={}, phoneNumber={}", request.getName(), request.getPhoneNumber());
        
        // 중복 확인 전 현재 데이터 상태 로깅
        long totalUsers = userRepository.count();
        boolean phoneExists = userRepository.existsByPhoneNumber(request.getPhoneNumber());

        log.info("현재 users 테이블 상태: 총 사용자 수={}, 전화번호 존재={}", 
                totalUsers, phoneExists);
        
        // 실제 데이터 조회해서 로깅
        userRepository.findByPhoneNumber(request.getPhoneNumber()).ifPresent(user -> {
            log.warn("이미 존재하는 사용자: userId={}, name={}, phoneNumber={}, ci={}", 
                    user.getId(), user.getName(), user.getPhoneNumber(), user.getCi());
        });
        
        // 전화번호 중복 확인
        if (phoneExists) {
            log.error("전화번호 중복 감지: {}", request.getPhoneNumber());
            throw new BusinessException(ErrorCode.DUPLICATED_PHONE_NUMBER);
        }
        
        // 휴대폰 인증 여부 확인
        if (!phoneVerificationService.isPhoneVerified(request.getPhoneNumber())) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
        
        // 주민등록번호 체크섬 검증
        if (!validateSocialSecurityNumber(request.getSocialSecurityNumber())) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_SECURITY_NUMBER);
        }
        
        // CI 생성 (주민등록번호 + 099)
        String ci = generateCi(request.getSocialSecurityNumber());
        
        // 생년월일과 성별 추출
        LocalDate birthDate = extractBirthDate(request.getSocialSecurityNumber());
        String gender = extractGender(request.getSocialSecurityNumber());
        
        // 사용자 생성
        User user = User.builder()
                .name(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .phoneVerified(true)
                .ci(ci)
                .birthDate(birthDate)
                .gender(gender)
                .email(request.getEmail())
                .build();
        
        try {
            user = userRepository.save(user);
            log.info("오픈뱅킹 회원가입 완료: userId={}, name={}, ci={}", 
                    user.getId(), user.getName(), ci);
        } catch (DataIntegrityViolationException e) {
            log.error("데이터 제약조건 위반: {}", e.getMessage());
            String errorMessage = e.getMessage();
            
            if (errorMessage != null) {
                if (errorMessage.contains("phone_number") || errorMessage.contains("phoneNumber")) {
                    throw new BusinessException(ErrorCode.DUPLICATED_PHONE_NUMBER);
                } else if (errorMessage.contains("ci")) {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미 등록된 사용자입니다.");
                }
            }
            throw e;
        }
        
        return OpenBankingRegisterResponse.builder()
                .name(user.getName())
                .ci(user.getCi())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .build();
    }
    
    /**
     * CI (Connecting Information) 값 생성
     * 금융결제원 방식: CI = HMAC-SHA512((RN || Padding) ⊕ SA)
     * RN: 주민등록번호 (13byte)
     * SA: 비밀정보 "099" (64byte로 패딩)
     * SK: 비밀키 (64byte)
     */
    private String generateCi(String socialSecurityNumber) {
        try {
            // 1. RN: 주민등록번호 (13byte)
            byte[] rn = socialSecurityNumber.getBytes(StandardCharsets.UTF_8);
            
            // 2. Padding: 512bit(64byte)로 맞추기 위해 패딩 추가
            byte[] rnWithPadding = new byte[64];
            System.arraycopy(rn, 0, rnWithPadding, 0, Math.min(rn.length, 64));
            // 나머지는 0x00으로 채워짐 (기본값)
            
            // 3. SA: 비밀정보 "099"를 64byte로 패딩
            byte[] sa = new byte[64];
            byte[] saData = "099".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(saData, 0, sa, 0, Math.min(saData.length, 64));
            
            // 4. (RN || Padding) ⊕ SA : XOR 연산
            byte[] xorResult = new byte[64];
            for (int i = 0; i < 64; i++) {
                xorResult[i] = (byte) (rnWithPadding[i] ^ sa[i]);
            }
            
            // 5. SK: 비밀키 (KISA와 공유하는 키 - 고정값 사용)
            String secretKey = "KFTC-OPENBANKING-SECRET-KEY-FOR-CI-GENERATION-2024-DEMO-VERSION";
            byte[] sk = secretKey.getBytes(StandardCharsets.UTF_8);
            
            // 6. HMAC-SHA512 계산
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(sk, "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] hmacResult = mac.doFinal(xorResult);
            
            // 7. Base64 인코딩하여 88byte CI 생성
            String ci = Base64.getEncoder().encodeToString(hmacResult);
            
            // 88byte로 제한 (Base64 특성상 더 길 수 있음)
            if (ci.length() > 88) {
                ci = ci.substring(0, 88);
            }
            
            log.info("CI 생성 완료: ssn={}, ci length={}", 
                    socialSecurityNumber.substring(0, 6) + "******", ci.length());
            
            return ci;
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC-SHA512 처리 중 오류: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "CI 생성 중 오류가 발생했습니다.");
        }
    }
    
    /**
     * 주민등록번호에서 생년월일 추출
     */
    private LocalDate extractBirthDate(String socialSecurityNumber) {
        try {
            String yearPrefix = socialSecurityNumber.substring(0, 2);
            String month = socialSecurityNumber.substring(2, 4);
            String day = socialSecurityNumber.substring(4, 6);
            char genderCode = socialSecurityNumber.charAt(6);
            
            int yearNum = Integer.parseInt(yearPrefix);
            int monthNum = Integer.parseInt(month);
            int dayNum = Integer.parseInt(day);
            
            // 성별코드로 세기 판단
            int fullYear;
            if (genderCode == '1' || genderCode == '2') {
                fullYear = 1900 + yearNum;
            } else if (genderCode == '3' || genderCode == '4') {
                fullYear = 2000 + yearNum;
            } else {
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_SECURITY_NUMBER, "유효하지 않은 성별코드입니다.");
            }
            
            return LocalDate.of(fullYear, monthNum, dayNum);
            
        } catch (Exception e) {
            log.error("생년월일 추출 중 오류: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_SECURITY_NUMBER, "주민등록번호에서 생년월일 추출에 실패했습니다.");
        }
    }
    
    /**
     * 주민등록번호에서 성별 추출
     */
    private String extractGender(String socialSecurityNumber) {
        try {
            char genderCode = socialSecurityNumber.charAt(6);
            
            if (genderCode == '1' || genderCode == '3') {
                return "M"; // 남성
            } else if (genderCode == '2' || genderCode == '4') {
                return "F"; // 여성
            } else {
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_SECURITY_NUMBER, "유효하지 않은 성별코드입니다.");
            }
            
        } catch (Exception e) {
            log.error("성별 추출 중 오류: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_SECURITY_NUMBER, "주민등록번호에서 성별 추출에 실패했습니다.");
        }
    }
    
    /**
     * 주민등록번호 체크섬 검증
     */
    private boolean validateSocialSecurityNumber(String ssn) {
        if (ssn == null || ssn.length() != 13) {
            log.warn("주민등록번호 길이 오류: {}", ssn != null ? ssn.length() : "null");
            return false;
        }
        
        try {
            // 숫자만 확인
            for (char c : ssn.toCharArray()) {
                if (!Character.isDigit(c)) {
                    log.warn("주민등록번호에 숫자가 아닌 문자 포함");
                    return false;
                }
            }
            
            // 체크섬 검증
            int[] weights = {2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5};
            int sum = 0;
            
            for (int i = 0; i < 12; i++) {
                sum += Character.getNumericValue(ssn.charAt(i)) * weights[i];
            }
            
            int remainder = sum % 11;
            int checkDigit = (11 - remainder) % 10;
            
            int actualCheckDigit = Character.getNumericValue(ssn.charAt(12));
            
            boolean isValid = checkDigit == actualCheckDigit;
            
            log.info("주민등록번호 검증: {} -> {}", 
                    ssn.substring(0, 6) + "******", 
                    isValid ? "유효" : "무효");
            
            return isValid;
            
        } catch (Exception e) {
            log.error("주민등록번호 검증 중 오류: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 전체 사용자 목록 조회 (개발용)
     */
    @Transactional(readOnly = true)
    public java.util.List<User> getAllMembers() {
        return userRepository.findAll();
    }
    
    /**
     * 휴대폰번호로 사용자 조회
     */
    @Transactional(readOnly = true)
    public User getMemberByPhone(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "해당 휴대폰번호로 등록된 사용자가 없습니다."));
    }
    
    /**
     * 휴대폰번호로 사용자 삭제 (개발용)
     */
    public void deleteMemberByPhone(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "해당 휴대폰번호로 등록된 사용자가 없습니다."));
        
        userRepository.delete(user);
        log.info("사용자 삭제 완료: userId={}, phoneNumber={}", user.getId(), phoneNumber);
    }
    
    /**
     * KFTC 오픈뱅킹 연동
     */
    public String connectKftc(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        
        if (!user.isPhoneVerified()) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "휴대폰 인증이 필요합니다.");
        }
        
        // KFTC 인증 URL 생성
        String authUrl = kftcInternalService.generateAuthUrl(userId.toString());
        
        log.info("KFTC 연동 시작: userId={}, authUrl={}", userId, authUrl);
        
        return authUrl;
    }
    
    /**
     * KFTC OAuth 콜백 처리
     */
    public UserRegisterResponse handleKftcCallback(String code, String state) {
        try {
            Long userId = Long.parseLong(state);
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
            
            // KFTC 토큰 발급
            KftcTokenResponse tokenResponse = kftcInternalService.getAccessToken(code);
            
            // 사용자 정보에 토큰 저장
            user.updateKftcTokens(
                    tokenResponse.getUserSeqNo(),
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken()
            );
            
            log.info("KFTC 연동 완료: userId={}, userSeqNo={}", userId, tokenResponse.getUserSeqNo());
            
            return UserRegisterResponse.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .phoneNumber(user.getPhoneNumber())
                    .phoneVerified(user.isPhoneVerified())
                    .ci(user.getCi())
                    .userSeqNo(user.getUserSeqNo())
                    .accessToken(user.getAccessToken())
                    .kftcAuthUrl(null)  // 이미 KFTC 연동 완료
                    .build();
            
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "유효하지 않은 state 값입니다.");
        }
    }
    
    /**
     * 사용자 정보 조회 (UserRegisterResponse 형태)
     */
    @Transactional(readOnly = true)
    public UserRegisterResponse getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        
        // KFTC 연동이 안 되어 있으면 인증 URL 제공
        String kftcAuthUrl = (user.getAccessToken() == null) ? 
                kftcInternalService.generateAuthUrl(userId.toString()) : null;
        
        return UserRegisterResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .phoneVerified(user.isPhoneVerified())
                .ci(user.getCi())
                .userSeqNo(user.getUserSeqNo())
                .accessToken(user.getAccessToken())
                .kftcAuthUrl(kftcAuthUrl)
                .build();
    }
    
} 