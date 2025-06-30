package com.kftc.user.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.user.dto.OpenBankingRegisterRequest;
import com.kftc.user.dto.OpenBankingRegisterResponse;
import com.kftc.user.dto.KftcTokenResponse;
import com.kftc.user.dto.UserRegisterResponse;
import com.kftc.user.entity.User;
import com.kftc.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final KftcInternalService kftcInternalService;
    
    /**
     * 오픈뱅킹 회원가입 처리
     */
    public OpenBankingRegisterResponse registerMember(OpenBankingRegisterRequest request) {
        log.info("=== 오픈뱅킹 회원가입 시작 ===");
        log.info("요청 정보: name={}, phoneNumber={}", request.getName(), request.getPhoneNumber());
        
        // 중복 확인
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
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
        
        // CI 생성
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
                .userStatus(User.UserStatus.ACTIVE)
                .userType(User.UserType.PERSONAL)
                .build();
        
        user.generateJoinDate();
        
        try {
            user = userRepository.save(user);
            log.info("오픈뱅킹 회원가입 완료: userId={}, name={}, ci={}", 
                    user.getId(), user.getName(), ci);
        } catch (DataIntegrityViolationException e) {
            handleDataIntegrityViolation(e);
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
     * CI로 사용자 찾기 또는 생성 (OAuth용)
     */
    public User findOrCreateUserByCi(String ci) {
        Optional<User> existingUser = userRepository.findByCiAndUserStatus(ci, User.UserStatus.ACTIVE);
        if (existingUser.isPresent()) {
            log.info("기존 사용자 발견: userSeqNum={}", existingUser.get().getUserSeqNum());
            return existingUser.get();
        }
        
        // 새 사용자 생성
        String userSeqNum = generateNextUserSeqNum();
        User newUser = User.builder()
                .userSeqNum(userSeqNum)
                .ci(ci)
                .name("인증사용자_" + System.currentTimeMillis())
                .phoneNumber("00000000000") // 임시값
                .phoneVerified(false)
                .userStatus(User.UserStatus.ACTIVE)
                .userType(User.UserType.PERSONAL)
                .build();
        
        newUser.generateJoinDate();
        User savedUser = userRepository.save(newUser);
        log.info("새 사용자 생성 완료: userSeqNum={}, ci={}", 
                savedUser.getUserSeqNum(), ci.substring(0, 10) + "...");
        
        return savedUser;
    }
    
    /**
     * 사용자 조회 메서드들
     */
    @Transactional(readOnly = true)
    public User findByCi(String ci) {
        return userRepository.findByCi(ci)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "해당 CI의 사용자를 찾을 수 없습니다."));
    }
    
    @Transactional(readOnly = true)
    public User findByUserSeqNum(String userSeqNum) {
        return userRepository.findByUserSeqNum(userSeqNum)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "해당 사용자 일련번호의 사용자를 찾을 수 없습니다."));
    }
    
    @Transactional(readOnly = true)
    public User findByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "해당 휴대폰번호로 등록된 사용자가 없습니다."));
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findActiveUserByCi(String ci) {
        return userRepository.findByCiAndUserStatus(ci, User.UserStatus.ACTIVE);
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findActiveUserByUserSeqNum(String userSeqNum) {
        return userRepository.findByUserSeqNumAndUserStatus(userSeqNum, User.UserStatus.ACTIVE);
    }
    
    /**
     * 사용자 정보 업데이트
     */
    public void updateKftcTokens(Long userId, String userSeqNum, String accessToken, String refreshToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        
        user.updateKftcTokens(userSeqNum, accessToken, refreshToken);
        userRepository.save(user);
        
        log.info("KFTC 토큰 업데이트 완료: userId={}, userSeqNum={}", userId, userSeqNum);
    }
    
    public void markPhoneVerified(String phoneNumber) {
        User user = findByPhoneNumber(phoneNumber);
        user.updatePhoneVerified();
        userRepository.save(user);
        
        log.info("휴대폰 인증 완료 처리: phoneNumber={}", phoneNumber);
    }
    
    /**
     * KFTC 연동
     */
    public String generateAuthUrl(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        
        if (!user.isPhoneVerified()) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "휴대폰 인증이 필요합니다.");
        }
        
        return kftcInternalService.generateAuthUrl(userId.toString());
    }
    
    @Transactional(readOnly = true)
    public UserRegisterResponse getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        
        String kftcAuthUrl = (user.getAccessToken() == null) ? 
                kftcInternalService.generateAuthUrl(userId.toString()) : null;
        
        return UserRegisterResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .phoneVerified(user.isPhoneVerified())
                .ci(user.getCi())
                .userSeqNo(user.getUserSeqNum())
                .accessToken(user.getAccessToken())
                .kftcAuthUrl(kftcAuthUrl)
                .build();
    }
    
    /**
     * KFTC OAuth 콜백 처리
     */
    public UserRegisterResponse handleKftcCallback(String code, String state) {
        try {
            Long userId = Long.parseLong(state);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
            
            // KFTC에서 토큰 발급
            KftcTokenResponse tokenResponse = kftcInternalService.getAccessToken(code);
            
            // 사용자 정보 업데이트
            user.updateKftcTokens(tokenResponse.getUserSeqNo(), 
                                tokenResponse.getAccessToken(), 
                                tokenResponse.getRefreshToken());
            userRepository.save(user);
            
            log.info("KFTC 콜백 처리 완료: userId={}, userSeqNo={}", userId, tokenResponse.getUserSeqNo());
            
            return UserRegisterResponse.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .phoneNumber(user.getPhoneNumber())
                    .phoneVerified(user.isPhoneVerified())
                    .ci(user.getCi())
                    .userSeqNo(user.getUserSeqNum())
                    .accessToken(user.getAccessToken())
                    .build();
            
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "유효하지 않은 state 값입니다.");
        }
    }
    
    /**
     * 조회 및 관리 기능
     */
    @Transactional(readOnly = true)
    public List<User> getAllMembers() {
        return userRepository.findAll();
    }
    
    public void deleteMemberByPhone(String phoneNumber) {
        User user = findByPhoneNumber(phoneNumber);
        userRepository.delete(user);
        log.info("사용자 삭제 완료: userId={}, phoneNumber={}", user.getId(), phoneNumber);
    }
    
    /**
     * 통계 조회
     */
    @Transactional(readOnly = true)
    public long getTotalActiveUserCount() {
        return userRepository.countByUserStatus(User.UserStatus.ACTIVE);
    }
    
    @Transactional(readOnly = true)
    public long getPersonalUserCount() {
        return userRepository.countByUserTypeAndUserStatus(User.UserType.PERSONAL, User.UserStatus.ACTIVE);
    }
    
    @Transactional(readOnly = true)
    public long getCorporateUserCount() {
        return userRepository.countByUserTypeAndUserStatus(User.UserType.CORPORATE, User.UserStatus.ACTIVE);
    }
    
    /**
     * Private 헬퍼 메서드들
     */
    private void handleDataIntegrityViolation(DataIntegrityViolationException e) {
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
    
    private String generateNextUserSeqNum() {
        Optional<String> maxUserSeqNum = userRepository.findMaxUserSeqNum();
        
        if (maxUserSeqNum.isEmpty()) {
            return "1000000001";
        }
        
        try {
            long currentMax = Long.parseLong(maxUserSeqNum.get());
            long nextSeqNum = currentMax + 1;
            return String.format("%010d", nextSeqNum);
        } catch (NumberFormatException e) {
            log.error("사용자 일련번호 파싱 실패: {}", maxUserSeqNum.get());
            throw new BusinessException(ErrorCode.INVALID_VALUE, "사용자 일련번호 생성에 실패했습니다.");
        }
    }
    
    /**
     * CI 생성 (HMAC-SHA512 기반)
     */
    private String generateCi(String socialSecurityNumber) {
        try {
            byte[] rn = socialSecurityNumber.getBytes(StandardCharsets.UTF_8);
            
            byte[] rnWithPadding = new byte[64];
            System.arraycopy(rn, 0, rnWithPadding, 0, Math.min(rn.length, 64));
            
            byte[] sa = new byte[64];
            byte[] saData = "099".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(saData, 0, sa, 0, Math.min(saData.length, 64));
            
            byte[] xorResult = new byte[64];
            for (int i = 0; i < 64; i++) {
                xorResult[i] = (byte) (rnWithPadding[i] ^ sa[i]);
            }
            
            String secretKey = "KFTC-OPENBANKING-SECRET-KEY-FOR-CI-GENERATION-2024-DEMO-VERSION";
            byte[] sk = secretKey.getBytes(StandardCharsets.UTF_8);
            
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(sk, "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] hmacResult = mac.doFinal(xorResult);
            
            String ci = Base64.getEncoder().encodeToString(hmacResult);
            
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
    
    private LocalDate extractBirthDate(String socialSecurityNumber) {
        try {
            String yearPrefix = socialSecurityNumber.substring(0, 2);
            String month = socialSecurityNumber.substring(2, 4);
            String day = socialSecurityNumber.substring(4, 6);
            char genderCode = socialSecurityNumber.charAt(6);
            
            int yearNum = Integer.parseInt(yearPrefix);
            int monthNum = Integer.parseInt(month);
            int dayNum = Integer.parseInt(day);
            
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
            log.error("생년월일 추출 실패: ssn={}, error={}", 
                    socialSecurityNumber.substring(0, 6) + "******", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_SECURITY_NUMBER, "주민등록번호에서 생년월일 추출에 실패했습니다.");
        }
    }
    
    private String extractGender(String socialSecurityNumber) {
        try {
            char genderCode = socialSecurityNumber.charAt(6);
            return (genderCode == '1' || genderCode == '3') ? "M" : "F";
        } catch (Exception e) {
            log.error("성별 추출 실패: ssn={}, error={}", 
                    socialSecurityNumber.substring(0, 6) + "******", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_SECURITY_NUMBER, "주민등록번호에서 성별 추출에 실패했습니다.");
        }
    }
    
    private boolean validateSocialSecurityNumber(String ssn) {
        if (ssn == null || ssn.length() != 13) {
            return false;
        }
        
        try {
            for (char c : ssn.toCharArray()) {
                if (!Character.isDigit(c)) {
                    return false;
                }
            }
            
            int[] multipliers = {2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5};
            int sum = 0;
            
            for (int i = 0; i < 12; i++) {
                sum += Character.getNumericValue(ssn.charAt(i)) * multipliers[i];
            }
            
            int remainder = sum % 11;
            int checkDigit = (11 - remainder) % 10;
            
            return checkDigit == Character.getNumericValue(ssn.charAt(12));
            
        } catch (Exception e) {
            log.error("주민등록번호 체크섬 검증 실패: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * OpenBankingController에서 사용하는 메서드들
     */
    @Transactional(readOnly = true)
    public User getMemberByPhone(String phoneNumber) {
        return findByPhoneNumber(phoneNumber);
    }
    
    public String connectKftc(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
        
        if (!user.isPhoneVerified()) {
            throw new BusinessException(ErrorCode.INVALID_VALUE, "휴대폰 인증이 필요합니다.");
        }
        
        return kftcInternalService.generateAuthUrl(userId.toString());
    }
} 