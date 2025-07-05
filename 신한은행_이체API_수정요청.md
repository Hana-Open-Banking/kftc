# 신한은행 이체 API 수정 요청

## 🚨 긴급 수정 요청

KFTC 오픈뱅킹 API 명세에 따라 이체 API 엔드포인트를 다음과 같이 **긴급 수정**해주시기 바랍니다.

---

## 📋 수정 사항 요약

### 1. URL 경로 변경
- **기존**: `/v2.0/transfer/withdraw/{fintech_use_num}`
- **변경**: `/v2.0/transfer/withdraw/fin_num`

- **기존**: `/v2.0/transfer/deposit/{fintech_use_num}`  
- **변경**: `/v2.0/transfer/deposit/fin_num`

### 2. fintech_use_num 위치 변경
- **기존**: Path Parameter
- **변경**: Request Body에 포함

---

## 🔧 상세 수정 내용

### 출금이체 API

#### 수정된 엔드포인트
```
POST /v2.0/transfer/withdraw/fin_num
```

#### 수정된 Request Body
```json
{
  "bank_tran_id": "BANK20241205001234",
  "cntr_account_type": "N",
  "cntr_account_num": "110-123-456789",
  "dps_print_content": "일반이체",
  "fintech_use_num": "199000000000000000000001",  // ⭐ Body에 포함
  "tran_amt": "100000",
  "tran_dtime": "20241205143000",
  "req_client_name": "김고객",
  "req_client_bank_code": "088",
  "req_client_account_num": "110-123-456789",
  "req_client_num": "",
  "recv_client_name": "박수취인",
  "recv_client_bank_code": "004",
  "recv_client_account_num": "123-456-789012",
  "transfer_purpose": "일반이체",
  "recv_client_num": "",
  "req_from_offline_yn": "N"
}
```

### 입금이체 API

#### 수정된 엔드포인트
```
POST /v2.0/transfer/deposit/fin_num
```

#### 수정된 Request Body
```json
{
  "bank_tran_id": "BANK20241205001235",
  "cntr_account_type": "N",
  "cntr_account_num": "110-123-456789",
  "dps_print_content": "일반이체",
  "fintech_use_num": "199000000000000000000001",  // ⭐ Body에 포함
  "tran_amt": "100000",
  "tran_dtime": "20241205143000",
  "recv_client_name": "김고객",
  "recv_client_bank_code": "088",
  "recv_client_account_num": "110-123-456789",
  "req_client_name": "박송금인",
  "req_client_bank_code": "004",
  "req_client_account_num": "123-456-789012",
  "req_client_num": "",
  "transfer_purpose": "일반이체",
  "req_from_offline_yn": "N"
}
```

---

## 💻 Spring Boot 컨트롤러 수정 예시

### 수정 전
```java
@PostMapping("/v2.0/transfer/withdraw/{fintech_use_num}")
public ResponseEntity<Map<String, Object>> withdrawTransfer(
        @PathVariable("fintech_use_num") String fintechUseNum,
        @RequestBody Map<String, Object> request) {
    // ...
}
```

### 수정 후
```java
@PostMapping("/v2.0/transfer/withdraw/fin_num")
public ResponseEntity<Map<String, Object>> withdrawTransfer(
        @RequestBody Map<String, Object> request) {
    
    // fintech_use_num을 request body에서 추출
    String fintechUseNum = (String) request.get("fintech_use_num");
    
    // ...
}
```

---

## 🛠️ 필수 수정 작업

### 1. 컨트롤러 수정
```java
@RestController
@RequestMapping("/v2.0/transfer")
public class ShinhanTransferController {
    
    // ✅ 수정된 출금이체 엔드포인트
    @PostMapping("/withdraw/fin_num")
    public ResponseEntity<Map<String, Object>> withdrawTransfer(
            @RequestBody Map<String, Object> request) {
        
        String fintechUseNum = (String) request.get("fintech_use_num");
        
        // 기존 로직 유지
        return transferService.processWithdraw(fintechUseNum, request);
    }
    
    // ✅ 수정된 입금이체 엔드포인트
    @PostMapping("/deposit/fin_num")
    public ResponseEntity<Map<String, Object>> depositTransfer(
            @RequestBody Map<String, Object> request) {
        
        String fintechUseNum = (String) request.get("fintech_use_num");
        
        // 기존 로직 유지
        return transferService.processDeposit(fintechUseNum, request);
    }
}
```

### 2. Request Body 검증 추가
```java
// fintech_use_num 필수 검증
if (request.get("fintech_use_num") == null || 
    request.get("fintech_use_num").toString().isEmpty()) {
    return ResponseEntity.badRequest().body(
        createErrorResponse("A0023", "핀테크이용번호가 필요합니다")
    );
}
```

### 3. 응답 형식 유지
- 기존 응답 구조는 **변경 없음**
- 처리 로직도 **변경 없음**
- 단순히 **엔드포인트 경로**와 **파라미터 받는 방식**만 변경

---

## 🔍 테스트 방법

### 수정된 API 테스트
```bash
# 출금이체 테스트
curl -X POST "http://localhost:8080/v2.0/transfer/withdraw/fin_num" \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: KFTC_BANK_API_KEY_2024" \
  -H "X-CLIENT-ID: KFTC_CENTER" \
  -H "X-BANK-CODE: 088" \
  -d '{
    "fintech_use_num": "199000000000000000000001",
    "bank_tran_id": "BANK20241205001234",
    "tran_amt": "100000",
    "recv_client_name": "박수취인",
    "recv_client_bank_code": "004",
    "recv_client_account_num": "123-456-789012",
    "transfer_purpose": "일반이체",
    "req_from_offline_yn": "N"
  }'
```

---

## ⚠️ 중요 공지

1. **기존 엔드포인트 제거**: `/v2.0/transfer/withdraw/{fintech_use_num}` 삭제
2. **새 엔드포인트 추가**: `/v2.0/transfer/withdraw/fin_num` 생성
3. **하위 호환성**: 기존 엔드포인트는 **즉시 중단** 예정
4. **테스트 완료 후 배포**: 반드시 테스트 검증 후 운영 반영

---

## 📞 문의사항

이 수정 작업에 대한 문의사항이 있으시면 KFTC 개발팀으로 연락주시기 바랍니다.

**수정 완료 예상 시간**: 2시간 이내  
**배포 일정**: 당일 중  
**검증 방법**: Postman 테스트 및 통합 테스트

---

**⚡ 긴급 수정 요청이므로 최우선으로 처리 부탁드립니다! ⚡** 