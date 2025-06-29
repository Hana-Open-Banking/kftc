package com.kftc.user.controller;

import com.kftc.common.dto.BasicResponse;
import com.kftc.user.dto.OpenBankingRegisterRequest;
import com.kftc.user.dto.OpenBankingRegisterResponse;
import com.kftc.user.dto.UserRegisterResponse;
import com.kftc.user.service.OpenBankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "오픈뱅킹 회원가입", description = "오픈뱅킹 회원가입 API")
@RestController
@RequestMapping("/api/openbanking")
@RequiredArgsConstructor
@Slf4j
public class OpenBankingController {
    
    private final OpenBankingService openBankingService;
    
    /**
     * 오픈뱅킹 회원가입 페이지
     */
    @Operation(summary = "오픈뱅킹 회원가입 페이지", description = "사용자 정보 입력 폼을 제공합니다.")
    @GetMapping("/signup")
    public String signupForm() {
        // 실제로는 HTML 페이지를 반환하겠지만, 현재는 문자열로 대체
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>금융결제원 오픈뱅킹 회원가입</title>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; max-width: 600px; margin: 50px auto; padding: 20px; }
                    .form-group { margin: 15px 0; }
                    label { display: block; margin-bottom: 5px; font-weight: bold; }
                    input { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 4px; }
                    button { background-color: #007bff; color: white; padding: 12px 30px; border: none; border-radius: 4px; cursor: pointer; }
                    button:hover { background-color: #0056b3; }
                    .header { text-align: center; margin-bottom: 30px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>금융결제원 오픈뱅킹</h1>
                    <h2>회원가입</h2>
                </div>
                <form id="signupForm">
                    <div class="form-group">
                        <label for="name">이름 *</label>
                        <input type="text" id="name" name="name" required>
                    </div>
                    <div class="form-group">
                        <label for="socialSecurityNumber">주민등록번호 *</label>
                        <input type="text" id="socialSecurityNumber" name="socialSecurityNumber" 
                               placeholder="13자리 숫자 (예: 9001011234567)" maxlength="13" required>
                    </div>
                    <div class="form-group">
                        <label for="phoneNumber">휴대폰번호 *</label>
                        <input type="text" id="phoneNumber" name="phoneNumber" 
                               placeholder="01012345678" maxlength="11" required>
                    </div>
                    <div class="form-group">
                        <label for="email">이메일</label>
                        <input type="email" id="email" name="email" placeholder="선택사항">
                    </div>
                    <div class="form-group">
                        <button type="button" onclick="sendVerification()">휴대폰 인증</button>
                    </div>
                    <div class="form-group">
                        <button type="button" onclick="submitForm()">회원가입</button>
                    </div>
                </form>
                
                <script>
                    function sendVerification() {
                        const phoneNumber = document.getElementById('phoneNumber').value;
                        if (!phoneNumber) {
                            alert('휴대폰번호를 입력해주세요.');
                            return;
                        }
                        
                        fetch('/api/phone-verification/send', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ phoneNumber: phoneNumber })
                        })
                        .then(response => response.json())
                        .then(data => {
                            if (data.status === 200) {
                                const code = prompt('휴대폰으로 전송된 인증번호를 입력해주세요:');
                                if (code) {
                                    verifyCode(phoneNumber, code);
                                }
                            } else {
                                alert('인증번호 발송 실패: ' + data.message);
                            }
                        });
                    }
                    
                    function verifyCode(phoneNumber, code) {
                        fetch('/api/phone-verification/verify', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ phoneNumber: phoneNumber, verificationCode: code })
                        })
                        .then(response => response.json())
                        .then(data => {
                            if (data.status === 200) {
                                alert('휴대폰 인증이 완료되었습니다.');
                            } else {
                                alert('인증 실패: ' + data.message);
                            }
                        });
                    }
                    
                    function submitForm() {
                        const formData = {
                            name: document.getElementById('name').value,
                            socialSecurityNumber: document.getElementById('socialSecurityNumber').value,
                            phoneNumber: document.getElementById('phoneNumber').value,
                            email: document.getElementById('email').value
                        };
                        
                        fetch('/api/openbanking/register', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(formData)
                        })
                        .then(response => response.json())
                                                 .then(data => {
                             if (data.status === 200) {
                                 const member = data.data;
                                 alert(`회원가입이 완료되었습니다!
회원정보:
- 이름: ${member.name}
- CI: ${member.ci}
- 생년월일: ${member.birthDate}
- 성별: ${member.gender === 'M' ? '남성' : '여성'}
- 휴대폰: ${member.phoneNumber}
- 이메일: ${member.email || '없음'}`);
                                 console.log('회원정보:', data.data);
                             } else {
                                 alert('회원가입 실패: ' + data.message);
                             }
                         });
                    }
                </script>
            </body>
            </html>
            """;
    }
    
    /**
     * 오픈뱅킹 회원가입 처리
     */
    @Operation(summary = "오픈뱅킹 회원가입", description = "회원가입을 처리하고 회원 정보를 반환합니다.")
    @PostMapping("/register")
    public ResponseEntity<BasicResponse> registerMember(
            @Valid @RequestBody OpenBankingRegisterRequest request) {
        
        log.info("오픈뱅킹 회원가입 요청: name={}, phoneNumber={}", 
                request.getName(), request.getPhoneNumber());
        
        OpenBankingRegisterResponse response = openBankingService.registerMember(request);
        
        BasicResponse basicResponse = BasicResponse.builder()
                .status(200)
                .message("회원가입이 성공적으로 완료되었습니다.")
                .data(response)
                .build();
        
        return ResponseEntity.ok(basicResponse);
    }
    
    /**
     * 전체 사용자 목록 조회 (개발용)
     */
    @Operation(summary = "전체 사용자 목록 조회", description = "개발 및 테스트용 전체 사용자 목록을 조회합니다.")
    @GetMapping("/users")
    public ResponseEntity<BasicResponse> getAllUsers() {
        
        log.info("전체 사용자 목록 조회 요청");
        
        java.util.List<com.kftc.user.entity.User> users = openBankingService.getAllMembers();
        
        BasicResponse basicResponse = BasicResponse.builder()
                .status(200)
                .message("사용자 목록 조회 성공")
                .data(users)
                .build();
        
        return ResponseEntity.ok(basicResponse);
    }
    
    /**
     * 휴대폰번호로 사용자 조회
     */
    @Operation(summary = "휴대폰번호로 사용자 조회", description = "휴대폰번호로 사용자 정보를 조회합니다.")
    @GetMapping("/users/phone/{phoneNumber}")
    public ResponseEntity<BasicResponse> getUserByPhone(@PathVariable String phoneNumber) {
        
        log.info("휴대폰번호로 사용자 조회: phoneNumber={}", phoneNumber);
        
        try {
            com.kftc.user.entity.User user = openBankingService.getMemberByPhone(phoneNumber);
            
            BasicResponse basicResponse = BasicResponse.builder()
                    .status(200)
                    .message("사용자 조회 성공")
                    .data(user)
                    .build();
            
            return ResponseEntity.ok(basicResponse);
            
        } catch (com.kftc.common.exception.BusinessException e) {
            BasicResponse basicResponse = BasicResponse.builder()
                    .status(404)
                    .message("해당 휴대폰번호로 등록된 사용자가 없습니다.")
                    .data(null)
                    .build();
            
            return ResponseEntity.status(404).body(basicResponse);
        }
    }
    
    /**
     * 휴대폰번호로 사용자 삭제 (개발용)
     */
    @Operation(summary = "휴대폰번호로 사용자 삭제", description = "개발 및 테스트용 사용자 삭제 기능입니다.")
    @DeleteMapping("/users/phone/{phoneNumber}")
    public ResponseEntity<BasicResponse> deleteUserByPhone(@PathVariable String phoneNumber) {
        
        log.info("휴대폰번호로 사용자 삭제: phoneNumber={}", phoneNumber);
        
        try {
            openBankingService.deleteMemberByPhone(phoneNumber);
            
            BasicResponse basicResponse = BasicResponse.builder()
                    .status(200)
                    .message("사용자 삭제 성공")
                    .data(null)
                    .build();
            
            return ResponseEntity.ok(basicResponse);
            
        } catch (com.kftc.common.exception.BusinessException e) {
            BasicResponse basicResponse = BasicResponse.builder()
                    .status(404)
                    .message("해당 휴대폰번호로 등록된 사용자가 없습니다.")
                    .data(null)
                    .build();
            
            return ResponseEntity.status(404).body(basicResponse);
        }
    }
    
    /**
     * KFTC 오픈뱅킹 연동
     */
    @Operation(summary = "KFTC 오픈뱅킹 연동", description = "사용자 ID로 KFTC 오픈뱅킹 인증 URL을 생성합니다.")
    @PostMapping("/users/{userId}/connect-kftc")
    public ResponseEntity<BasicResponse> connectKftc(@PathVariable Long userId) {
        
        log.info("KFTC 연동 요청: userId={}", userId);
        
        String authUrl = openBankingService.connectKftc(userId);
        
        BasicResponse response = BasicResponse.builder()
                .status(200)
                .message("KFTC 인증 URL이 생성되었습니다.")
                .data(authUrl)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 사용자 정보 조회
     */
    @Operation(summary = "사용자 정보 조회", description = "사용자 ID로 사용자 정보를 조회합니다.")
    @GetMapping("/users/{userId}")
    public ResponseEntity<BasicResponse> getUserInfo(@PathVariable Long userId) {
        
        log.info("사용자 정보 조회: userId={}", userId);
        
        UserRegisterResponse response = openBankingService.getUserInfo(userId);
        
        BasicResponse basicResponse = BasicResponse.builder()
                .status(200)
                .message("사용자 정보 조회 성공")
                .data(response)
                .build();
        
        return ResponseEntity.ok(basicResponse);
    }
} 