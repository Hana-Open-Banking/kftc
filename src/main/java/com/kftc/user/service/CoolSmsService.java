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
import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

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
        try {
            // SSL 인증서 검증 우회 설정
            setupSSLContext();
            
            this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.coolsms.co.kr");
            log.info("CoolSMS 서비스 초기화 완료");
        } catch (Exception e) {
            log.error("CoolSMS 서비스 초기화 실패: {}", e.getMessage());
        }
    }
    
    /**
     * SSL 컨텍스트 설정 - 인증서 검증 우회
     */
    private void setupSSLContext() throws Exception {
        // 모든 인증서를 신뢰하는 TrustManager 생성
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };
        
        // SSL 컨텍스트 생성 및 설정
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        // 기본 SSL 소켓 팩토리로 설정
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        
        // 호스트명 검증 비활성화
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        
        log.info("SSL 컨텍스트 설정 완료");
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
            message.setText(String.format("[PASS] 본인확인 인증번호는 [%s]입니다. 타인 노출 금지", verificationCode));
            
            SingleMessageSentResponse response = this.messageService.sendOne(new SingleMessageSendingRequest(message));
            
            log.info("SMS 발송 성공: phoneNumber={}, messageId={}", phoneNumber, response.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("SMS 발송 실패: phoneNumber={}, error={}", phoneNumber, e.getMessage());
            return false;
        }
    }
} 