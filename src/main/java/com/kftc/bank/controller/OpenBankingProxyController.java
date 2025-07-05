package com.kftc.bank.controller;

import com.kftc.bank.service.BankService;
import com.kftc.bank.common.BankAccountInfo;
import com.kftc.bank.common.BankCode;
import com.kftc.bank.common.TransferRequest;
import com.kftc.bank.common.TransferResponse;
import com.kftc.common.dto.BasicResponse;
import com.kftc.oauth.config.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "오픈뱅킹 프록시", description = "오픈뱅킹 API 프록시 서비스")
@RestController
@RequiredArgsConstructor
@Slf4j
public class OpenBankingProxyController {
    
    private final BankService bankService;
    
    /**
     * SecurityContext에서 인증된 사용자 정보 가져오기
     */
    private JwtAuthenticationFilter.JwtAuthenticatedUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtAuthenticationFilter.JwtAuthenticatedUser) {
            return (JwtAuthenticationFilter.JwtAuthenticatedUser) authentication.getPrincipal();
        }
        throw new IllegalStateException("인증된 사용자 정보를 찾을 수 없습니다.");
    }
    
    @GetMapping("/v2.0/user/me")
    @Operation(summary = "사용자정보조회", description = "오픈뱅킹 사용자의 기본정보를 조회합니다.",
               security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<BasicResponse> getUserInfo(
            @Parameter(description = "사용자일련번호") @RequestParam(value = "user_seq_no", required = false) String userSeqNo) {
        
        log.info("=== /v2.0/user/me API 호출 시작 ===");
        log.info("요청된 user_seq_no: [{}]", userSeqNo);
        
        try {
            // JWT 토큰에서 사용자 정보 추출
            log.info("JWT 토큰에서 사용자 정보 추출 시도...");
            JwtAuthenticationFilter.JwtAuthenticatedUser authenticatedUser = getAuthenticatedUser();
            log.info("JWT 인증 사용자 정보: {}", authenticatedUser);
            
            // user_seq_no가 없으면 JWT에서 추출
            if (userSeqNo == null || userSeqNo.trim().isEmpty()) {
                userSeqNo = authenticatedUser.getUserId();
                log.info("user_seq_no가 비어있어서 JWT에서 추출: [{}]", userSeqNo);
            }
            
            log.info("최종 사용할 userSeqNo: [{}]", userSeqNo);
            log.info("멀티 기관 사용자정보조회 API 호출: userSeqNo={}", userSeqNo);
            
            // 멀티 기관 통합 조회
            Map<String, Object> integratedResult = bankService.getUserInfoFromAllInstitutions(userSeqNo);
            log.info("멀티 기관 통합 조회 결과: {}", integratedResult);
            
            BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("사용자정보조회가 성공적으로 완료되었습니다.")
                .data(integratedResult)
                .build();
            
            log.info("=== /v2.0/user/me API 호출 성공 ===");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("사용자정보조회 실패: userSeqNo={}, error={}", userSeqNo, e.getMessage(), e);
            
            BasicResponse response = BasicResponse.builder()
                .status(400)
                .message("사용자정보조회에 실패했습니다: " + e.getMessage())
                .data(null)
                .build();
            
            log.info("=== /v2.0/user/me API 호출 실패 ===");
            return ResponseEntity.badRequest().body(response);
                }
    }
    
    @GetMapping("/v2.0/account/list")
    @Operation(summary = "계좌목록조회", description = "오픈뱅킹 연동 계좌 목록을 조회합니다.",
               security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<BasicResponse> getAccountList() {
        
        log.info("계좌목록조회 API 호출");
        
        try {
            // 인증된 사용자 정보 가져오기
            JwtAuthenticationFilter.JwtAuthenticatedUser authenticatedUser = getAuthenticatedUser();
            
            List<BankAccountInfo> accounts = bankService.getAccountList(authenticatedUser.getAccessToken());
            
            BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("계좌목록조회가 성공적으로 완료되었습니다.")
                .data(accounts)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("계좌목록조회 실패: {}", e.getMessage());
            
            BasicResponse response = BasicResponse.builder()
                .status(400)
                .message("계좌목록조회에 실패했습니다: " + e.getMessage())
                .data(null)
                .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/v2.0/account/balance/{fintech_use_num}")
    @Operation(summary = "계좌잔액조회", description = "특정 계좌의 잔액을 조회합니다.",
               security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<BasicResponse> getAccountBalance(
            @Parameter(description = "핀테크이용번호") @PathVariable("fintech_use_num") String fintechUseNum) {
        
        log.info("계좌잔액조회 API 호출: fintechUseNum={}", fintechUseNum);
        
        try {
            // 인증된 사용자 정보 가져오기
            JwtAuthenticationFilter.JwtAuthenticatedUser authenticatedUser = getAuthenticatedUser();
            
            BankAccountInfo accountInfo = bankService.getAccountBalance(fintechUseNum, authenticatedUser.getAccessToken());
            
            BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("계좌잔액조회가 성공적으로 완료되었습니다.")
                .data(accountInfo)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("계좌잔액조회 실패: fintechUseNum={}, error={}", fintechUseNum, e.getMessage());
            
            BasicResponse response = BasicResponse.builder()
                .status(400)
                .message("계좌잔액조회에 실패했습니다: " + e.getMessage())
                .data(null)
                .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/v2.0/account/transaction_list/{fintech_use_num}")
    @Operation(summary = "거래내역조회", description = "특정 계좌의 거래내역을 조회합니다.",
               security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<BasicResponse> getTransactionList(
            @Parameter(description = "핀테크이용번호") @PathVariable("fintech_use_num") String fintechUseNum) {
        
        log.info("거래내역조회 API 호출: fintechUseNum={}", fintechUseNum);
        
        try {
            // 인증된 사용자 정보 가져오기
            JwtAuthenticationFilter.JwtAuthenticatedUser authenticatedUser = getAuthenticatedUser();
            
            List<Object> transactions = bankService.getTransactionList(fintechUseNum, authenticatedUser.getAccessToken());
            
            BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("거래내역조회가 성공적으로 완료되었습니다.")
                .data(transactions)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("거래내역조회 실패: fintechUseNum={}, error={}", fintechUseNum, e.getMessage());
            
            BasicResponse response = BasicResponse.builder()
                .status(400)
                .message("거래내역조회에 실패했습니다: " + e.getMessage())
                .data(null)
                .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/v2.0/transfer/withdraw/fin_num")
    @Operation(summary = "출금이체", description = "출금이체를 실행합니다.",
               security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<BasicResponse> withdrawTransfer(@RequestBody TransferRequest request) {
        
        String fintechUseNum = request.getEffectiveFintechUseNum();
        log.info("출금이체 API 호출: fintechUseNum={}, 이체금액={}", fintechUseNum, request.getTranAmt());
        
        try {
            // 인증된 사용자 정보 가져오기
            JwtAuthenticationFilter.JwtAuthenticatedUser authenticatedUser = getAuthenticatedUser();
            
            // 출금이체 실행
            TransferResponse transferResponse = bankService.withdrawTransfer(fintechUseNum, request, authenticatedUser.getAccessToken());
            
            BasicResponse response = BasicResponse.builder()
                .status(transferResponse.getRspCode().equals("A0000") ? 200 : 400)
                .message(transferResponse.getRspMessage())
                .data(transferResponse)
                .build();
            
            if (transferResponse.getRspCode().equals("A0000")) {
                log.info("출금이체 성공: fintechUseNum={}, 거래고유번호={}", fintechUseNum, transferResponse.getBankTranId());
                return ResponseEntity.ok(response);
            } else {
                log.error("출금이체 실패: fintechUseNum={}, 오류코드={}, 오류메시지={}", 
                    fintechUseNum, transferResponse.getRspCode(), transferResponse.getRspMessage());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("출금이체 실패: fintechUseNum={}, error={}", fintechUseNum, e.getMessage(), e);
            
            BasicResponse response = BasicResponse.builder()
                .status(500)
                .message("출금이체 처리 중 오류가 발생했습니다: " + e.getMessage())
                .data(null)
                .build();
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/v2.0/transfer/deposit/fin_num")
    @Operation(summary = "입금이체", description = "입금이체를 실행합니다.",
               security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<BasicResponse> depositTransfer(@RequestBody TransferRequest request) {
        
        String fintechUseNum = request.getEffectiveFintechUseNum();
        log.info("입금이체 API 호출: fintechUseNum={}, 이체금액={}", fintechUseNum, request.getTranAmt());
        
        try {
            // 인증된 사용자 정보 가져오기
            JwtAuthenticationFilter.JwtAuthenticatedUser authenticatedUser = getAuthenticatedUser();
            
            // 입금이체 실행
            TransferResponse transferResponse = bankService.depositTransfer(fintechUseNum, request, authenticatedUser.getAccessToken());
            
            BasicResponse response = BasicResponse.builder()
                .status(transferResponse.getRspCode().equals("A0000") ? 200 : 400)
                .message(transferResponse.getRspMessage())
                .data(transferResponse)
                .build();
            
            if (transferResponse.getRspCode().equals("A0000")) {
                log.info("입금이체 성공: fintechUseNum={}, 거래고유번호={}", fintechUseNum, transferResponse.getBankTranId());
                return ResponseEntity.ok(response);
            } else {
                log.error("입금이체 실패: fintechUseNum={}, 오류코드={}, 오류메시지={}", 
                    fintechUseNum, transferResponse.getRspCode(), transferResponse.getRspMessage());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("입금이체 실패: fintechUseNum={}, error={}", fintechUseNum, e.getMessage(), e);
            
            BasicResponse response = BasicResponse.builder()
                .status(500)
                .message("입금이체 처리 중 오류가 발생했습니다: " + e.getMessage())
                .data(null)
                .build();
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/v1.0/inquiry/real_name")
    @Operation(summary = "계좌실명조회", description = "계좌번호의 실명을 조회합니다.",
               security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<BasicResponse> inquiryRealName(
            @Parameter(description = "은행 거래고유번호") @RequestParam String bank_tran_id,
            @Parameter(description = "계좌번호") @RequestParam String account_num,
            @Parameter(description = "예금주정보타입") @RequestParam String account_holder_info_type,
            @Parameter(description = "예금주정보") @RequestParam String account_holder_info) {
        
        log.info("계좌실명조회 API 호출: account_num={}", account_num);
        
        try {
            // 인증된 사용자 정보 가져오기
            JwtAuthenticationFilter.JwtAuthenticatedUser authenticatedUser = getAuthenticatedUser();
            
            // 신한은행 코드로 고정 (실제로는 토큰에서 추출)
            String bankCode = "088";
            BankAccountInfo result = bankService.verifyAccountRealName(bankCode, account_num, authenticatedUser.getAccessToken());
            
            BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("계좌실명조회가 성공적으로 완료되었습니다.")
                .data(result)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("계좌실명조회 실패: account_num={}, error={}", account_num, e.getMessage());
            
            BasicResponse response = BasicResponse.builder()
                .status(400)
                .message("계좌실명조회에 실패했습니다: " + e.getMessage())
                .data(null)
                .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    // 기타 유틸리티 API
    
    @GetMapping("/supported-banks")
    @Operation(summary = "지원 은행 목록", description = "현재 지원하는 은행 목록을 조회합니다.")
    public ResponseEntity<BasicResponse> getSupportedBanks() {
        log.info("지원 은행 목록 조회 API 호출");

        try {
            List<BankCode> supportedBanks = bankService.getSupportedBanks();
            
            BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("지원 은행 목록 조회가 성공적으로 완료되었습니다.")
                .data(supportedBanks)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("지원 은행 목록 조회 실패: {}", e.getMessage());
            
            BasicResponse response = BasicResponse.builder()
                .status(500)
                .message("지원 은행 목록 조회에 실패했습니다: " + e.getMessage())
                .data(null)
                .build();
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/{bankCode}/health")
    @Operation(summary = "은행 연결 상태 확인", description = "특정 은행과의 연결 상태를 확인합니다.")
    public ResponseEntity<BasicResponse> checkHealth(
            @Parameter(description = "은행 코드") @PathVariable String bankCode) {
        log.info("은행 연결 상태 확인 API 호출: bankCode={}", bankCode);
        
        try {
            boolean isHealthy = bankService.isHealthy(bankCode);
            
            BasicResponse response = BasicResponse.builder()
                .status(isHealthy ? 200 : 503)
                .message(isHealthy ? "은행 서비스가 정상적으로 동작하고 있습니다." : "은행 서비스에 문제가 있습니다.")
                .data(isHealthy ? "HEALTHY" : "UNHEALTHY")
                .build();
            
            return isHealthy ? ResponseEntity.ok(response) : ResponseEntity.status(503).body(response);
            
        } catch (Exception e) {
            log.error("은행 연결 상태 확인 실패: bankCode={}, error={}", bankCode, e.getMessage());
            
            BasicResponse response = BasicResponse.builder()
                .status(400)
                .message("은행 연결 상태 확인에 실패했습니다: " + e.getMessage())
                .data("ERROR")
                .build();
            
            return ResponseEntity.badRequest().body(response);
        }
    }
} 