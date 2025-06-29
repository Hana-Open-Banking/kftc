package com.kftc.user.service;

import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;

@Slf4j
@Service
public class CoolSmsService {
    
    @Value("${coolsms.api-key}")
    private String apiKey;
    
    @Value("${coolsms.api-secret}")
    private String apiSecret;
    
    @Value("${coolsms.from-number}")
    private String fromNumber;
    
    private DefaultMessageService messageService;
    private final SecureRandom random = new SecureRandom();
    
    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
    }
    
    /**
     * 6자리 인증번호 생성
     */
    public String generateVerificationCode() {
        return String.format("%06d", random.nextInt(1000000));
    }
    
    /**
     * SMS 인증번호 발송
     */
    public boolean sendVerificationSms(String phoneNumber, String verificationCode) {
        try {
            Message message = new Message();
            message.setFrom(fromNumber);
            message.setTo(phoneNumber);
            message.setText(String.format("[KFTC] 본인확인 인증번호는 [%s]입니다. 타인 노출 금지", verificationCode));
            
            SingleMessageSentResponse response = this.messageService.sendOne(new SingleMessageSendingRequest(message));
            
            log.info("SMS 발송 성공: phoneNumber={}, messageId={}", phoneNumber, response.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("SMS 발송 실패: phoneNumber={}, error={}", phoneNumber, e.getMessage());
            return false;
        }
    }
} 