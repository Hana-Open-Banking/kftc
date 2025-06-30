package com.kftc.user.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "kftc_users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User extends DateTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "user_sequence", allocationSize = 1)
    private Long id;
    
    @Column(name = "user_seq_num", unique = true, length = 10)
    private String userSeqNum; // KFTC에서 발급받은 사용자 일련번호
    
    @Column(name = "ci", length = 88, unique = true)
    private String ci; // 사용자 고유 CI 값 (Connecting Information)
    
    @Column(nullable = false, length = 50)
    private String name;
    
    @Column(name = "phone_number", nullable = false, unique = true, length = 11)
    private String phoneNumber; // 휴대폰번호
    
    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private boolean phoneVerified = false; // 휴대폰 인증 여부
    
    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", length = 20, nullable = false)
    @Builder.Default
    private UserStatus userStatus = UserStatus.ACTIVE;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 10, nullable = false)
    @Builder.Default
    private UserType userType = UserType.PERSONAL;
    
    // KFTC 토큰 정보
    @Column(name = "access_token", length = 1000)
    private String accessToken; // KFTC 액세스 토큰
    
    @Column(name = "refresh_token", length = 1000)
    private String refreshToken; // KFTC 리프레시 토큰
    
    // 개인정보 (주민등록번호에서 추출)
    @Column(name = "birth_date")
    private LocalDate birthDate; // 생년월일
    
    @Column(name = "gender", length = 1)
    private String gender; // 성별 (M/F)
    
    @Column(name = "email", length = 100)
    private String email; // 이메일 (선택사항)
    
    @Column(name = "join_date", length = 8)
    private String joinDate; // 가입일자 (YYYYMMDD)
    
    // 비즈니스 메서드
    public void updatePhoneVerified() {
        this.phoneVerified = true;
    }
    
    public void updateCi(String ci) {
        this.ci = ci;
    }
    
    public void updateKftcTokens(String userSeqNum, String accessToken, String refreshToken) {
        this.userSeqNum = userSeqNum;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
    
    public void updatePersonalInfo(LocalDate birthDate, String gender, String email) {
        this.birthDate = birthDate;
        this.gender = gender;
        this.email = email;
    }
    
    public void updateUserInfo(String name, String email, String phoneNumber) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }
    
    public void updateStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }
    
    public boolean isActive() {
        return this.userStatus == UserStatus.ACTIVE;
    }
    
    public void generateJoinDate() {
        this.joinDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
    
    // Enum 정의
    public enum UserType {
        PERSONAL("개인"),
        CORPORATE("법인");
        
        private final String description;
        
        UserType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum UserStatus {
        ACTIVE("활성"),
        INACTIVE("비활성"),
        SUSPENDED("중지"),
        WITHDRAWN("탈퇴");
        
        private final String description;
        
        UserStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
} 