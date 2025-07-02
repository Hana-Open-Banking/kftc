package com.kftc.common.controller;

import com.kftc.common.entity.TransactionLog;
import com.kftc.common.repository.TransactionLogRepository;
import com.kftc.common.util.TransactionIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2.0/transaction")
@Tag(name = "Transaction Management", description = "거래고유번호 생성 및 관리 API")
public class TransactionController {
    
    private final TransactionIdGenerator transactionIdGenerator;
    private final TransactionLogRepository transactionLogRepository;
    
    @PostMapping("/generate-id")
    @Operation(
        summary = "거래고유번호 생성",
        description = "오픈뱅킹 API 호출용 거래고유번호(참가기관)를 생성합니다. 하루 동안 유일성이 보장됩니다."
    )
    public ResponseEntity<Map<String, Object>> generateTransactionId(
            @Parameter(description = "API명 (예: GET /v2.0/cards)", required = true)
            @RequestParam("api_name") String apiName,
            @Parameter(description = "사용자일련번호", required = false)
            @RequestParam(value = "user_seq_no", required = false) String userSeqNo,
            @Parameter(description = "카드사대표코드", required = false)
            @RequestParam(value = "bank_code_std", required = false) String bankCodeStd) {
        
        try {
            log.info("거래고유번호 생성 요청 - API: {}, 사용자: {}", apiName, userSeqNo);
            
            // 거래고유번호 생성 및 DB 저장
            TransactionLog transactionLog = transactionIdGenerator.generateAndSaveTransactionId(
                apiName, userSeqNo, bankCodeStd);
            
            Map<String, Object> response = new HashMap<>();
            response.put("rsp_code", "A0000");
            response.put("rsp_message", "거래고유번호 생성 성공");
            response.put("bank_tran_id", transactionLog.getTransactionId());
            response.put("transaction_date", transactionLog.getTransactionDate().toString());
            response.put("api_name", transactionLog.getApiName());
            response.put("created_at", transactionLog.getCreatedAt());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("거래고유번호 생성 실패", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("rsp_code", "A9999");
            response.put("rsp_message", "거래고유번호 생성 실패: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/today-logs")
    @Operation(
        summary = "오늘 생성된 거래 로그 조회",
        description = "오늘 생성된 모든 거래고유번호와 상태를 조회합니다."
    )
    public ResponseEntity<Map<String, Object>> getTodayTransactionLogs() {
        
        LocalDate today = LocalDate.now();
        List<TransactionLog> logs = transactionLogRepository.findByTransactionDateOrderByCreatedAtDesc(today);
        
        Map<String, Object> response = new HashMap<>();
        response.put("rsp_code", "A0000");
        response.put("rsp_message", "조회 성공");
        response.put("transaction_date", today.toString());
        response.put("total_count", logs.size());
        response.put("transaction_logs", logs);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/logs/{transactionId}")
    @Operation(
        summary = "특정 거래고유번호 조회",
        description = "특정 거래고유번호의 상세 정보를 조회합니다."
    )
    public ResponseEntity<Map<String, Object>> getTransactionLog(
            @Parameter(description = "거래고유번호", required = true)
            @PathVariable("transactionId") String transactionId,
            @Parameter(description = "거래일자 (YYYY-MM-DD)", required = false)
            @RequestParam(value = "transaction_date", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate transactionDate) {
        
        if (transactionDate == null) {
            transactionDate = LocalDate.now(); // 오늘 날짜로 기본 설정
        }
        
        var logOptional = transactionLogRepository.findByTransactionDateAndTransactionId(
            transactionDate, transactionId);
        
        Map<String, Object> response = new HashMap<>();
        
        if (logOptional.isPresent()) {
            TransactionLog log = logOptional.get();
            response.put("rsp_code", "A0000");
            response.put("rsp_message", "조회 성공");
            response.put("transaction_log", log);
        } else {
            response.put("rsp_code", "A0001");
            response.put("rsp_message", "해당 거래고유번호가 존재하지 않습니다");
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/logs/{transactionId}/complete")
    @Operation(
        summary = "거래 완료 처리",
        description = "API 호출 완료 후 결과를 업데이트합니다."
    )
    public ResponseEntity<Map<String, Object>> completeTransaction(
            @Parameter(description = "거래고유번호", required = true)
            @PathVariable("transactionId") String transactionId,
            @RequestBody Map<String, Object> request) {
        
        LocalDate today = LocalDate.now();
        var logOptional = transactionLogRepository.findByTransactionDateAndTransactionId(today, transactionId);
        
        Map<String, Object> response = new HashMap<>();
        
        if (logOptional.isPresent()) {
            TransactionLog log = logOptional.get();
            
            String responseCode = (String) request.get("response_code");
            String responseMessage = (String) request.get("response_message");
            Long processingTimeMs = request.get("processing_time_ms") != null ? 
                Long.valueOf(request.get("processing_time_ms").toString()) : 0L;
            
            log.completeTransaction(responseCode, responseMessage, processingTimeMs);
            transactionLogRepository.save(log);
            
            response.put("rsp_code", "A0000");
            response.put("rsp_message", "거래 완료 처리 성공");
            response.put("transaction_log", log);
        } else {
            response.put("rsp_code", "A0001");
            response.put("rsp_message", "해당 거래고유번호가 존재하지 않습니다");
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/statistics")
    @Operation(
        summary = "거래 통계 조회",
        description = "기간별 거래 통계를 조회합니다."
    )
    public ResponseEntity<Map<String, Object>> getTransactionStatistics(
            @Parameter(description = "시작일자 (YYYY-MM-DD)")
            @RequestParam(value = "start_date", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료일자 (YYYY-MM-DD)")
            @RequestParam(value = "end_date", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        if (startDate == null) startDate = LocalDate.now().minusDays(7); // 기본 7일 전
        if (endDate == null) endDate = LocalDate.now(); // 기본 오늘
        
        List<Object[]> statistics = transactionLogRepository.getTransactionStatistics(startDate, endDate);
        
        Map<String, Object> response = new HashMap<>();
        response.put("rsp_code", "A0000");
        response.put("rsp_message", "통계 조회 성공");
        response.put("start_date", startDate.toString());
        response.put("end_date", endDate.toString());
        response.put("statistics", statistics);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/test/generate-multiple")
    @Operation(
        summary = "테스트용 거래고유번호 대량 생성",
        description = "테스트 목적으로 여러 개의 거래고유번호를 생성합니다."
    )
    public ResponseEntity<Map<String, Object>> generateMultipleTransactionIds(
            @Parameter(description = "생성할 개수 (최대 100개)")
            @RequestParam(value = "count", defaultValue = "5") int count) {
        
        if (count > 100) count = 100; // 최대 100개로 제한
        
        try {
            transactionIdGenerator.generateSampleTransactionIds(count);
            
            Map<String, Object> response = new HashMap<>();
            response.put("rsp_code", "A0000");
            response.put("rsp_message", count + "개 거래고유번호 생성 완료");
            response.put("generated_count", count);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("대량 거래고유번호 생성 실패", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("rsp_code", "A9999");
            response.put("rsp_message", "생성 실패: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 