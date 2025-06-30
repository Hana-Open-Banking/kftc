package com.kftc.user.service;

import com.kftc.common.exception.BusinessException;
import com.kftc.common.exception.ErrorCode;
import com.kftc.user.entity.PhoneVerification;
import com.kftc.user.repository.PhoneVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PhoneVerificationService {
    
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final CoolSmsService coolSmsService;
    
    /**
     * 휴대폰 인증 코드 발송
     */
    public void sendVerificationCode(String phoneNumber) {
        // 기존 인증 코드 삭제
        phoneVerificationRepository.deleteByPhoneNumber(phoneNumber);
        
        // 새 인증 코드 생성
        String verificationCode = coolSmsService.generateVerificationCode();
        
        // SMS 발송
        boolean smsResult = coolSmsService.sendVerificationSms(phoneNumber, verificationCode);
        if (!smsResult) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "SMS 발송에 실패했습니다.");
        }
        
        // 인증 정보 저장 (5분 유효)
        PhoneVerification phoneVerification = PhoneVerification.builder()
                .phoneNumber(phoneNumber)
                .verificationCode(verificationCode)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        
        phoneVerificationRepository.save(phoneVerification);
        
        log.info("휴대폰 인증 코드 발송 완료: phoneNumber={}", phoneNumber);
    }
    
    /**
     * 휴대폰 인증 코드 확인
     */
    public boolean verifyCode(String phoneNumber, String verificationCode) {
        Optional<PhoneVerification> verificationOpt = phoneVerificationRepository
                .findByPhoneNumberAndVerificationCodeAndVerifiedFalse(phoneNumber, verificationCode);
        
        if (verificationOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "유효하지 않은 인증 코드입니다.");
        }
        
        PhoneVerification verification = verificationOpt.get();
        
        if (verification.isExpired()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "만료된 인증 코드입니다.");
        }
        
        // 인증 완료 처리
        verification.markAsVerified();
        
        log.info("휴대폰 인증 완료: phoneNumber={}", phoneNumber);
        return true;
    }
    
    /**
     * PASS 인증 완료 (CI 포함 응답)
     */
    public Object verifyCodeWithPassAuth(String phoneNumber, String verificationCode, 
                                        String userName, String socialSecurityNumber) {
        // 기본 인증 처리
        boolean verified = verifyCode(phoneNumber, verificationCode);
        
        // 사용자 정보가 있으면 CI 포함 응답, 없으면 기본 응답
        if (userName != null && socialSecurityNumber != null) {
            // CI 생성 (실제로는 PASS API에서 받아옴)
            String ci = generateCi(userName, socialSecurityNumber, phoneNumber);
            
            // 생년월일과 성별 추출
            String birthDate = extractBirthDate(socialSecurityNumber);
            String gender = extractGender(socialSecurityNumber);
            
            log.info("PASS 인증 완료: phoneNumber={}, userName={}, ci={}***", 
                    phoneNumber, userName, ci.substring(0, 10));
            
            // CI 포함 응답 객체 생성
            return java.util.Map.of(
                "verified", true,
                "ci", ci,
                "userName", userName,
                "phoneNumber", phoneNumber,
                "birthDate", birthDate,
                "gender", gender,
                "timestamp", System.currentTimeMillis()
            );
        }
        
        // 기본 응답 (기존 호환성 유지)
        return verified;
    }
    
    /**
     * CI 생성 (실제 PASS 연동 시에는 PASS API를 통해 받아옴)
     */
    private String generateCi(String userName, String socialSecurityNumber, String phoneNumber) {
        // 실제로는 PASS에서 제공하는 CI 값을 사용해야 함
        // 여기서는 테스트용으로 일관된 CI 생성
        String rawData = userName + socialSecurityNumber + phoneNumber;
        String hash = String.valueOf(Math.abs(rawData.hashCode()));
        
        // CI는 88byte 고정 길이여야 함 (실제 PASS CI 형식 모방)
        String ci = "CI" + hash;
        while (ci.length() < 88) {
            ci += "0";
        }
        
        return ci.length() > 88 ? ci.substring(0, 88) : ci;
    }
    
    /**
     * 주민등록번호에서 생년월일 추출 (YYYYMMDD)
     */
    private String extractBirthDate(String socialSecurityNumber) {
        if (socialSecurityNumber.length() < 7) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "주민등록번호가 올바르지 않습니다.");
        }
        
        String cleanSsn = socialSecurityNumber.replace("-", "");
        String yearPrefix = cleanSsn.substring(0, 2);
        String monthDay = cleanSsn.substring(2, 6);
        
        // 뒷자리 첫 번째 숫자로 연도 판단
        char genderDigit = cleanSsn.charAt(6);
        String year;
        
        if (genderDigit == '1' || genderDigit == '2') {
            year = "19" + yearPrefix;
        } else if (genderDigit == '3' || genderDigit == '4') {
            year = "20" + yearPrefix;
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "주민등록번호가 올바르지 않습니다.");
        }
        
        return year + monthDay;
    }
    
    /**
     * 주민등록번호에서 성별 추출
     */
    private String extractGender(String socialSecurityNumber) {
        if (socialSecurityNumber.length() < 7) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "주민등록번호가 올바르지 않습니다.");
        }
        
        String cleanSsn = socialSecurityNumber.replace("-", "");
        char genderDigit = cleanSsn.charAt(6);
        return (genderDigit == '1' || genderDigit == '3') ? "M" : "F";
    }
    
    /**
     * 휴대폰 인증 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isPhoneVerified(String phoneNumber) {
        Optional<PhoneVerification> verificationOpt = phoneVerificationRepository
                .findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        
        return verificationOpt.map(PhoneVerification::isVerified).orElse(false);
    }
} 