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
     * 휴대폰 인증 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isPhoneVerified(String phoneNumber) {
        Optional<PhoneVerification> verificationOpt = phoneVerificationRepository
                .findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        
        return verificationOpt.map(PhoneVerification::isVerified).orElse(false);
    }
} 