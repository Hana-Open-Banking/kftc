package com.kftc.user.controller;

import com.kftc.common.dto.BasicResponse;
import com.kftc.user.dto.PhoneVerificationConfirmRequest;
import com.kftc.user.dto.PhoneVerificationRequest;
import com.kftc.user.service.PhoneVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/phone-verification")
@RequiredArgsConstructor
@Tag(name = "휴대폰 인증", description = "휴대폰 본인인증 API")
public class PhoneVerificationController {
    
    private final PhoneVerificationService phoneVerificationService;
    
    @Operation(summary = "휴대폰 인증 코드 발송", description = "입력한 휴대폰번호로 인증 코드를 발송합니다.")
    @PostMapping("/send")
    public ResponseEntity<BasicResponse> sendVerificationCode(
            @Valid @RequestBody PhoneVerificationRequest request) {
        
        phoneVerificationService.sendVerificationCode(request.getPhoneNumber());
        
        BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("인증 코드가 발송되었습니다.")
                .data(null)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "휴대폰 인증 코드 확인", 
               description = "발송된 인증 코드를 확인합니다. 사용자 정보(이름, 주민등록번호)가 포함된 경우 PASS 인증으로 처리하여 CI를 반환합니다.")
    @PostMapping("/verify")
    public ResponseEntity<BasicResponse> verifyCode(
            @Valid @RequestBody PhoneVerificationConfirmRequest request) {
        
        log.info("📱 ============= 휴대폰 인증 코드 확인 엔드포인트 호출됨 =============");
        log.info("📱 요청 데이터: phoneNumber={}, userName={}, socialSecurityNumber={}***", 
            request.getPhoneNumber(), request.getUserName(), 
            request.getSocialSecurityNumber() != null ? request.getSocialSecurityNumber().substring(0, 6) : "null");
        
        // PASS 인증 (사용자 정보가 있으면 CI 포함 응답, 없으면 기본 응답)
        Object result = phoneVerificationService.verifyCodeWithPassAuth(
                request.getPhoneNumber(), 
                request.getVerificationCode(),
                request.getUserName(),
                request.getSocialSecurityNumber()
        );
        
        log.info("📱 phoneVerificationService.verifyCodeWithPassAuth 결과: {}", result);
        
        // 응답 메시지 동적 설정
        String message;
        if (request.getUserName() != null && request.getSocialSecurityNumber() != null) {
            message = "PASS 본인인증이 완료되었습니다.";
        } else {
            message = "휴대폰 인증이 완료되었습니다.";
        }
        
        BasicResponse response = BasicResponse.builder()
                .status(200)
                .message(message)
                .data(result)
                .build();
        
        log.info("📱 클라이언트로 반환할 응답: {}", response);
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "금융기관 연동 동의", 
               description = "사용자가 선택한 금융기관들과 연동 동의를 완료합니다.")
    @PostMapping("/financial-consent")
    public ResponseEntity<BasicResponse> consentFinancialInstitutions(
            @RequestBody Map<String, Object> request) {
        
        String userSeqNo = (String) request.get("userSeqNo");
        @SuppressWarnings("unchecked")
        List<String> selectedBankCodes = (List<String>) request.get("selectedBankCodes");
        
        log.info("금융기관 연동 동의 요청: userSeqNo={}, selectedBankCodes={}", userSeqNo, selectedBankCodes);
        
        List<String> linkedInstitutions = phoneVerificationService.linkSelectedFinancialInstitutions(
            userSeqNo, selectedBankCodes);
        
        BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("금융기관 연동 동의가 완료되었습니다.")
                .data(Map.of(
                    "linkedInstitutions", linkedInstitutions,
                    "totalCount", linkedInstitutions.size()
                ))
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "휴대폰 인증 여부 확인", description = "해당 휴대폰번호의 인증 여부를 확인합니다.")
    @GetMapping("/status/{phoneNumber}")
    public ResponseEntity<BasicResponse> checkVerificationStatus(
            @PathVariable String phoneNumber) {
        
        boolean isVerified = phoneVerificationService.isPhoneVerified(phoneNumber);
        
        BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("휴대폰 인증 상태 조회 성공")
                .data(isVerified)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "계좌 목록 조회", 
               description = "휴대폰 인증 후 연동 가능한 계좌 목록을 조회합니다.")
    @PostMapping("/discover-accounts")
    public ResponseEntity<BasicResponse> discoverAccounts(
            @RequestBody Map<String, Object> request) {
        
        log.info("🎯 ============= 계좌 목록 조회 엔드포인트 호출됨 =============");
        
        String userSeqNo = (String) request.get("userSeqNo");
        String userCi = (String) request.get("userCi");
        
        log.info("🎯 계좌 목록 조회 요청: userSeqNo={}, userCi={}...", 
            userSeqNo, userCi != null ? userCi.substring(0, 10) : "null");
        log.info("🎯 전체 요청 데이터: {}", request);
        
        List<Map<String, Object>> institutions = phoneVerificationService.discoverAvailableFinancialInstitutions(
            userSeqNo, userCi);
        
        log.info("🎯 컨트롤러에서 받은 기관 목록: 크기={}, 내용={}", 
            institutions != null ? institutions.size() : 0, institutions);
        
        BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("계좌 목록 조회가 완료되었습니다.")
                .data(institutions)
                .build();
        
        log.info("🎯 클라이언트로 반환할 응답: {}", response);
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "선택한 계좌들 DB 저장", 
               description = "사용자가 선택한 계좌들만 DB에 저장합니다.")
    @PostMapping("/save-selected-accounts")
    public ResponseEntity<BasicResponse> saveSelectedAccounts(
            @RequestBody Map<String, Object> request) {
        
        String userSeqNo = (String) request.get("userSeqNo");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> selectedAccounts = (List<Map<String, Object>>) request.get("selectedAccounts");
        
        log.info("선택한 계좌 저장 요청: userSeqNo={}, selectedAccountsCount={}", 
            userSeqNo, selectedAccounts != null ? selectedAccounts.size() : 0);
        
        Map<String, Object> result = phoneVerificationService.saveSelectedAccounts(
            userSeqNo, selectedAccounts);
        
        BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("선택한 계좌들이 저장되었습니다.")
                .data(result)
                .build();
        
        return ResponseEntity.ok(response);
    }
} 