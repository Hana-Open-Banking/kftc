package com.kftc.common.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 거래고유번호 로그 테이블
 * 
 * 목적:
 * 1. 하루 동안 거래고유번호 유일성 보장
 * 2. 거래 추적 및 로그
 * 3. 중복 생성 방지
 */
@Entity
@Table(name = "transaction_log", 
       indexes = {
           @Index(name = "idx_transaction_date_id", columnList = "transactionDate,transactionId", unique = true),
           @Index(name = "idx_transaction_date", columnList = "transactionDate"),
           @Index(name = "idx_api_name", columnList = "apiName")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionLog extends DateTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 거래고유번호(참가기관) - 20자리
     * 예: F001234560U4BC34239Z
     */
    @Column(name = "transaction_id", nullable = false, length = 20)
    private String transactionId;
    
    /**
     * 거래 생성 일자 (유일성 검사용)
     * 하루 동안만 유일성 보장하면 되므로 날짜별로 인덱스
     */
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;
    
    /**
     * 호출한 API 명
     * 예: GET /v2.0/cards, POST /v2.0/cards/bills
     */
    @Column(name = "api_name", nullable = false, length = 100)
    private String apiName;
    
    /**
     * 요청 사용자 일련번호
     */
    @Column(name = "user_seq_no", length = 30)
    private String userSeqNo;
    
    /**
     * 카드사 대표코드
     */
    @Column(name = "bank_code_std", length = 10)
    private String bankCodeStd;
    
    /**
     * API 응답 상태 코드
     * 예: A0000, A0001
     */
    @Column(name = "response_code", length = 10)
    private String responseCode;
    
    /**
     * API 응답 메시지
     */
    @Column(name = "response_message", length = 200)
    private String responseMessage;
    
    /**
     * 처리 시간 (밀리초)
     */
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    /**
     * 거래 상태
     * SUCCESS, FAILED, PENDING
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 20)
    private TransactionStatus transactionStatus;
    
    @Builder
    public TransactionLog(String transactionId, LocalDate transactionDate, String apiName, 
                         String userSeqNo, String bankCodeStd, String responseCode, 
                         String responseMessage, Long processingTimeMs, TransactionStatus transactionStatus) {
        this.transactionId = transactionId;
        this.transactionDate = transactionDate;
        this.apiName = apiName;
        this.userSeqNo = userSeqNo;
        this.bankCodeStd = bankCodeStd;
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.processingTimeMs = processingTimeMs;
        this.transactionStatus = transactionStatus;
    }
    
    /**
     * 거래 완료 처리
     */
    public void completeTransaction(String responseCode, String responseMessage, long processingTimeMs) {
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
        this.processingTimeMs = processingTimeMs;
        this.transactionStatus = "A0000".equals(responseCode) ? TransactionStatus.SUCCESS : TransactionStatus.FAILED;
    }
    
    /**
     * 거래 상태 열거형
     */
    public enum TransactionStatus {
        PENDING("처리중"),
        SUCCESS("성공"), 
        FAILED("실패");
        
        private final String description;
        
        TransactionStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
} 