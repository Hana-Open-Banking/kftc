package com.kftc.insurance.controller;

import com.kftc.insurance.dto.*;
import com.kftc.insurance.service.InsuranceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2.0")
@Tag(name = "Insurance API (센터인증)", description = "오픈뱅킹 보험정보조회 서비스 API - 센터인증 전용")
public class InsuranceController {
    
    private final InsuranceService insuranceService;
    
    @GetMapping("/insurances")
    @Operation(
        summary = "보험목록조회 API",
        description = "오픈뱅킹센터에 등록된 사용자의 보험 계약 목록을 보험사별로 조회합니다."
    )
    public ResponseEntity<InsuranceListResponse> getInsuranceList(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "거래고유번호(참가기관)", required = true)
            @RequestParam("bank_tran_id") String bankTranId,
            @Parameter(description = "사용자일련번호", required = true)
            @RequestParam("user_seq_no") String userSeqNo,
            @Parameter(description = "보험사 대표코드", required = true)
            @RequestParam("bank_code_std") String bankCodeStd,
            @Parameter(description = "직전조회추적정보", required = false)
            @RequestParam(value = "befor_inquiry_trace_info", required = false) String beforInquiryTraceInfo) {
        
        log.info("보험목록조회 API 호출 - bankTranId: {}, userSeqNo: {}, bankCodeStd: {}", 
                bankTranId, userSeqNo, bankCodeStd);
        
        InsuranceListRequest request = new InsuranceListRequest();
        request.setBankTranId(bankTranId);
        request.setUserSeqNo(userSeqNo);
        request.setBankCodeStd(bankCodeStd);
        request.setBeforInquiryTraceInfo(beforInquiryTraceInfo);
        
        InsuranceListResponse response = insuranceService.getInsuranceList(request, authorization);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/insurances/payment")
    @Operation(
        summary = "보험납입정보조회 API",
        description = "오픈뱅킹센터에 등록된 사용자의 보험 납입 정보를 조회합니다."
    )
    public ResponseEntity<InsurancePaymentResponse> getInsurancePayment(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "보험납입정보조회 요청", required = true)
            @Valid @RequestBody InsurancePaymentRequest request) {
        
        log.info("보험납입정보조회 API 호출 - bankTranId: {}, userSeqNo: {}, insuNum: {}", 
                request.getBankTranId(), request.getUserSeqNo(), request.getInsuNum());
        
        InsurancePaymentResponse response = insuranceService.getInsurancePayment(request, authorization);
        
        return ResponseEntity.ok(response);
    }
} 