package com.kftc.card.controller;

import com.kftc.card.dto.CardUserRegisterRequest;
import com.kftc.card.dto.CardUserRegisterResponse;
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
@RequestMapping("/v2.0/user")
@Tag(name = "Card User API", description = "카드 사용자 관련 API")
public class CardUserController {
    
    private final CardUserService cardUserService;
    
    @PostMapping("/register_card")
    @Operation(
        summary = "카드사용자등록 (자체인증 이용기관용)",
        description = "자체인증 이용기관이 (신용/체크) 카드정보조회(제3자정보제공동의)를 요청한 사용자를 등록합니다."
    )
    public ResponseEntity<CardUserRegisterResponse> registerCardUser(
            @Parameter(description = "Authorization 헤더 - Bearer <access_token>", required = true)
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CardUserRegisterRequest request) {
        
        log.info("카드사용자등록 API 호출 - bankTranId: {}", request.getBankTranId());
        
        CardUserRegisterResponse response = cardUserService.registerCardUser(request, authorization);
        
        return ResponseEntity.ok(response);
    }
} 