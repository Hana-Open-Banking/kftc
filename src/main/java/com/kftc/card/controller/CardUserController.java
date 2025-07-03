package com.kftc.card.controller;

import com.kftc.card.dto.*;
import com.kftc.card.service.CardUserService;
import com.kftc.common.dto.BasicResponse;
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
@Tag(name = "Card API (센터인증)", description = "오픈뱅킹 카드정보조회 서비스 API - 센터인증 전용")
public class CardUserController {
    
    private final CardUserService cardUserService;
    
    @PostMapping("/cards/cancel")
    @Operation(
        summary = "카드조회해지",
        description = "(신용/체크)카드 정보 조회를 위해 오픈뱅킹센터에 등록했던 사용자등록(제3자정보제공동의)을 해지합니다."
    )
    public ResponseEntity<CardCancelResponse> cancelCardInquiry(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CardCancelRequest request) {
        
        log.info("카드조회해지 API 호출 - bankTranId: {}, userSeqNo: {}", 
                request.getBankTranId(), request.getUserSeqNo());
        
        CardCancelResponse response = cardUserService.cancelCardInquiry(request, authorization);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cards")
    @Operation(
        summary = "카드목록조회 API",
        description = "오픈뱅킹센터에 등록된 사용자의 (신용/체크)카드 발급 목록을 카드사별로 조회합니다."
    )
    public ResponseEntity<CardListResponse> getCardList(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "거래고유번호(참가기관)", required = true)
            @RequestParam("bank_tran_id") String bankTranId,
            @Parameter(description = "사용자일련번호", required = true)
            @RequestParam("user_seq_no") String userSeqNo,
            @Parameter(description = "카드사 대표코드", required = true)
            @RequestParam("bank_code_std") String bankCodeStd,
            @Parameter(description = "회원 금융회사 코드", required = true)
            @RequestParam("member_bank_code") String memberBankCode,
            @Parameter(description = "직전조회추적정보", required = false)
            @RequestParam(value = "befor_inquiry_trace_info", required = false) String beforInquiryTraceInfo) {
        
        log.info("카드목록조회 API 호출 - bankTranId: {}, userSeqNo: {}", bankTranId, userSeqNo);
        
        CardListRequest request = new CardListRequest();
        request.setBankTranId(bankTranId);
        request.setUserSeqNo(userSeqNo);
        request.setBankCodeStd(bankCodeStd);
        request.setMemberBankCode(memberBankCode);
        request.setBeforInquiryTraceInfo(beforInquiryTraceInfo);
        
        CardListResponse response = cardUserService.getCardList(request, authorization);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cards/issue_info")
    @Operation(
        summary = "카드기본정보조회 API",
        description = "(신용/체크)카드 구분, 결제계좌 등의 카드 기본정보를 조회합니다."
    )
    public ResponseEntity<CardIssueInfoResponse> getCardIssueInfo(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "거래고유번호(참가기관)", required = true)
            @RequestParam("bank_tran_id") String bankTranId,
            @Parameter(description = "사용자일련번호", required = true)
            @RequestParam("user_seq_no") String userSeqNo,
            @Parameter(description = "카드사 대표코드", required = true)
            @RequestParam("bank_code_std") String bankCodeStd,
            @Parameter(description = "회원 금융회사 코드", required = true)
            @RequestParam("member_bank_code") String memberBankCode,
            @Parameter(description = "카드 식별자", required = true)
            @RequestParam("card_id") String cardId) {
        
        log.info("카드기본정보조회 API 호출 - bankTranId: {}, cardId: {}", bankTranId, cardId);
        
        CardIssueInfoRequest request = new CardIssueInfoRequest();
        request.setBankTranId(bankTranId);
        request.setUserSeqNo(userSeqNo);
        request.setBankCodeStd(bankCodeStd);
        request.setMemberBankCode(memberBankCode);
        request.setCardId(cardId);
        
        CardIssueInfoResponse response = cardUserService.getCardIssueInfo(request, authorization);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cards/bills")
    @Operation(
        summary = "카드청구기본정보조회 API",
        description = "오픈뱅킹센터에 등록된 사용자의 월별 대금 청구 목록을 카드사별로 조회합니다."
    )
    public ResponseEntity<CardBillsResponse> getCardBills(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "거래고유번호(참가기관)", required = true)
            @RequestParam("bank_tran_id") String bankTranId,
            @Parameter(description = "사용자일련번호", required = true)
            @RequestParam("user_seq_no") String userSeqNo,
            @Parameter(description = "카드사 대표코드", required = true)
            @RequestParam("bank_code_std") String bankCodeStd,
            @Parameter(description = "회원 금융회사 코드", required = true)
            @RequestParam("member_bank_code") String memberBankCode,
            @Parameter(description = "조회 시작월(YYYYMM)", required = true)
            @RequestParam("from_month") String fromMonth,
            @Parameter(description = "조회 종료월(YYYYMM)", required = true)
            @RequestParam("to_month") String toMonth,
            @Parameter(description = "직전조회추적정보", required = false)
            @RequestParam(value = "befor_inquiry_trace_info", required = false) String beforInquiryTraceInfo) {
        
        log.info("카드청구기본정보조회 API 호출 - bankTranId: {}, fromMonth: {}, toMonth: {}", 
                bankTranId, fromMonth, toMonth);
        
        CardBillsRequest request = new CardBillsRequest();
        request.setBankTranId(bankTranId);
        request.setUserSeqNo(userSeqNo);
        request.setBankCodeStd(bankCodeStd);
        request.setMemberBankCode(memberBankCode);
        request.setFromMonth(fromMonth);
        request.setToMonth(toMonth);
        request.setBeforInquiryTraceInfo(beforInquiryTraceInfo);
        
        CardBillsResponse response = cardUserService.getCardBills(request, authorization);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cards/bills/detail")
    @Operation(
        summary = "카드청구상세정보조회 API",
        description = "오픈뱅킹센터에 등록된 사용자의 카드 청구 세부항목을 조회합니다."
    )
    public ResponseEntity<CardBillDetailResponse> getCardBillDetail(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "거래고유번호(참가기관)", required = true)
            @RequestParam("bank_tran_id") String bankTranId,
            @Parameter(description = "사용자일련번호", required = true)
            @RequestParam("user_seq_no") String userSeqNo,
            @Parameter(description = "카드사 대표코드", required = true)
            @RequestParam("bank_code_std") String bankCodeStd,
            @Parameter(description = "회원 금융회사 코드", required = true)
            @RequestParam("member_bank_code") String memberBankCode,
            @Parameter(description = "청구년월(YYYYMM)", required = true)
            @RequestParam("charge_month") String chargeMonth,
            @Parameter(description = "결제순번", required = true)
            @RequestParam("settlement_seq_no") String settlementSeqNo,
            @Parameter(description = "직전조회추적정보", required = false)
            @RequestParam(value = "befor_inquiry_trace_info", required = false) String beforInquiryTraceInfo) {
        
        log.info("카드청구상세정보조회 API 호출 - bankTranId: {}, chargeMonth: {}, settlementSeqNo: {}", 
                bankTranId, chargeMonth, settlementSeqNo);
        
        CardBillDetailRequest request = new CardBillDetailRequest();
        request.setBankTranId(bankTranId);
        request.setUserSeqNo(userSeqNo);
        request.setBankCodeStd(bankCodeStd);
        request.setMemberBankCode(memberBankCode);
        request.setChargeMonth(chargeMonth);
        request.setSettlementSeqNo(settlementSeqNo);
        request.setBeforInquiryTraceInfo(beforInquiryTraceInfo);
        
        CardBillDetailResponse response = cardUserService.getCardBillDetail(request, authorization);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cards/transactions")
    @Operation(
        summary = "카드거래내역조회 API",
        description = "오픈뱅킹센터에 등록된 사용자의 카드 거래내역을 조회합니다."
    )
    public ResponseEntity<CardTransactionResponse> getCardTransactions(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authorization,
            @Parameter(description = "거래고유번호(참가기관)", required = true)
            @RequestParam("bank_tran_id") String bankTranId,
            @Parameter(description = "사용자일련번호", required = true)
            @RequestParam("user_seq_no") String userSeqNo,
            @Parameter(description = "카드사 대표코드", required = true)
            @RequestParam("bank_code_std") String bankCodeStd,
            @Parameter(description = "회원 금융회사 코드", required = true)
            @RequestParam("member_bank_code") String memberBankCode,
            @Parameter(description = "카드 식별자", required = true)
            @RequestParam("card_id") String cardId,
            @Parameter(description = "조회 시작일자(YYYYMMDD)", required = true)
            @RequestParam("from_date") String fromDate,
            @Parameter(description = "조회 종료일자(YYYYMMDD)", required = true)
            @RequestParam("to_date") String toDate,
            @Parameter(description = "페이지 인덱스 (1부터 시작)", required = false)
            @RequestParam(value = "page_index", required = false, defaultValue = "1") String pageIndex,
            @Parameter(description = "직전조회추적정보", required = false)
            @RequestParam(value = "befor_inquiry_trace_info", required = false) String beforInquiryTraceInfo) {
        
        log.info("카드거래내역조회 API 호출 - bankTranId: {}, cardId: {}, fromDate: {}, toDate: {}", 
                bankTranId, cardId, fromDate, toDate);
        
        CardTransactionRequest request = new CardTransactionRequest();
        request.setBankTranId(bankTranId);
        request.setUserSeqNo(userSeqNo);
        request.setBankCodeStd(bankCodeStd);
        request.setMemberBankCode(memberBankCode);
        request.setCardId(cardId);
        request.setFromDate(fromDate);
        request.setToDate(toDate);
        request.setPageIndex(pageIndex);
        request.setBeforInquiryTraceInfo(beforInquiryTraceInfo);
        
        CardTransactionResponse response = cardUserService.getCardTransactions(request, authorization);
        
        return ResponseEntity.ok(response);
    }
} 