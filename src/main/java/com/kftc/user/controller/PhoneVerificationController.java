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
        
        // PASS 인증 (사용자 정보가 있으면 CI 포함 응답, 없으면 기본 응답)
        Object result = phoneVerificationService.verifyCodeWithPassAuth(
                request.getPhoneNumber(), 
                request.getVerificationCode(),
                request.getUserName(),
                request.getSocialSecurityNumber()
        );
        
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
} 