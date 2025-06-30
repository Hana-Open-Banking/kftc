package com.kftc.user.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kftc_users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User extends DateTimeEntity {
    
    @Id
    @Column(name = "user_seq_no", length = 10)
    private String userSeqNo; // 사용자일련번호
    
    @Column(name = "user_ci", length = 100, unique = true)
    private String userCi; // 사용자CI
    
    @Column(name = "user_type", length = 10, nullable = false)
    @Builder.Default
    private String userType = "PERSONAL"; // 사용자유형(PERSONAL)
    
    @Column(name = "user_status", length = 20, nullable = false)
    @Builder.Default
    private String userStatus = "ACTIVE"; // 사용자상태
    
    @Column(name = "user_name", length = 50, nullable = false)
    private String userName; // 사용자이름
    
    @Column(name = "user_email", length = 100)
    private String userEmail; // 이메일
    
    @Column(name = "user_info", length = 8)
    private String userInfo; // 생년월일(YYYYMMDD)
    

    
    // 비즈니스 메서드
    public void updateUserCi(String userCi) {
        this.userCi = userCi;
    }
    
    public void updateUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }
    
    public void updateUserInfo(String userName, String userEmail, String userInfo) {
        this.userName = userName;
        this.userEmail = userEmail;
        this.userInfo = userInfo;
    }
    
    public boolean isActive() {
        return "ACTIVE".equals(this.userStatus);
    }
} 