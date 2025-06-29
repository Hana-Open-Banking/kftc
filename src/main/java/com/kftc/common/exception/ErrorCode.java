package com.kftc.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(500, "C_001", "서버에 문제가 발생하였습니다."),
    METHOD_NOT_ALLOWED(405, "C_002", "잘못된 HTTP Request Method 입니다."),
    INVALID_INPUT_VALUE(400, "C_003", "적절하지 않은 요청 값입니다."),
    INVALID_TYPE_VALUE(400, "C_004", "요청 값의 타입이 잘못되었습니다."),
    ENTITY_NOT_FOUND(400, "C_005", "지정한 Entity를 찾을 수 없습니다."),

    LOCATION_NOT_FOUND(400, "L_001", "잘못된 위치 정보입니다. 1~17 사이의 숫자를 입력해주세요."),

    MEMBER_NOT_FOUND(404, "M_001", "존재하지 않는 사용자입니다."),
    DUPLICATED_USERNAME(409, "M_002", "이미 존재하는 아이디입니다."),
    INVALID_PASSWORD(401, "M_003", "비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(401, "M_004", "토큰이 만료되었습니다."),
    
    // OAuth 관련 에러 코드
    INVALID_VALUE(400, "O_001", "유효하지 않은 값입니다."),
    OAUTH_CLIENT_NOT_FOUND(404, "O_002", "OAuth 클라이언트를 찾을 수 없습니다."),
    OAUTH_CLIENT_AUTHENTICATION_FAILED(401, "O_003", "OAuth 클라이언트 인증에 실패했습니다."),
    INVALID_AUTHORIZATION_CODE(400, "O_004", "유효하지 않은 인증 코드입니다."),
    EXPIRED_AUTHORIZATION_CODE(400, "O_005", "만료된 인증 코드입니다."),
    INVALID_REFRESH_TOKEN(400, "O_006", "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(400, "O_007", "만료된 리프레시 토큰입니다."),

    // 카드 관련 에러 코드
    INVALID_AUTHORIZATION(401, "K_001", "Authorization 헤더가 올바르지 않습니다."),
    INVALID_SCOPE(400, "K_002", "서비스구분이 올바르지 않습니다. (cardinfo만 허용)"),
    INVALID_AGREEMENT(400, "K_003", "제3자정보제공동의가 필요합니다."),
    ALREADY_REGISTERED_USER(400, "A0324", "기등록된 조회서비스용 사용자 서비스"),
    USER_WITHDRAWAL_IN_PROGRESS(400, "A0019", "사용자탈퇴 처리중인 서비스"),
    USER_NOT_FOUND(400, "555", "해당 사용자 없음"),
    USER_REGISTRATION_ERROR(400, "464", "사용자 등록정보 이상"),
    
    // 사용자 등록 관련 에러 코드
    DUPLICATED_PHONE_NUMBER(409, "U_001", "이미 등록된 휴대폰번호입니다."),
    DUPLICATED_SOCIAL_SECURITY_NUMBER(409, "U_002", "이미 등록된 주민등록번호입니다."),
    PHONE_VERIFICATION_REQUIRED(400, "U_003", "휴대폰 인증이 필요합니다."),
    INVALID_SOCIAL_SECURITY_NUMBER(400, "U_004", "유효하지 않은 주민등록번호입니다.")
    ;

    private final int status;
    private final String code;
    private final String message;

    ErrorCode(int status, String code, String message) {
        this.status = status;
        this.message = message;
        this.code = code;
    }

}
