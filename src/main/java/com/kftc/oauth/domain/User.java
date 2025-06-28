package com.kftc.oauth.domain;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends DateTimeEntity {
    
    @Id
    @Column(name = "user_seq_num", length = 10)
    private String userSeqNum;
    
    @Column(name = "user_ci", length = 100, unique = true, nullable = false)
    private String userCi;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 10, nullable = false)
    private UserType userType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", length = 20, nullable = false)
    private UserStatus userStatus;
    
    @Column(name = "user_name", length = 20, nullable = false)
    private String userName;
    
    @Column(name = "user_email", length = 100)
    private String userEmail;
    
    @Column(name = "user_birth_date", length = 8)
    private String userBirthDate;
    
    @Column(name = "user_cell_no", length = 15)
    private String userCellNo;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "user_sex_type", length = 1)
    private UserSexType userSexType;
    
    @Column(name = "join_date", length = 8)
    private String joinDate;
    
    @Builder
    public User(String userSeqNum, String userCi, UserType userType, UserStatus userStatus,
                String userName, String userEmail, String userBirthDate, String userCellNo,
                UserSexType userSexType, String joinDate) {
        this.userSeqNum = userSeqNum;
        this.userCi = userCi;
        this.userType = userType;
        this.userStatus = userStatus;
        this.userName = userName;
        this.userEmail = userEmail;
        this.userBirthDate = userBirthDate;
        this.userCellNo = userCellNo;
        this.userSexType = userSexType;
        this.joinDate = joinDate;
    }
    
    // 비즈니스 메서드
    public void updateUserInfo(String userName, String userEmail, String userCellNo) {
        this.userName = userName;
        this.userEmail = userEmail;
        this.userCellNo = userCellNo;
    }
    
    public void updateStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }
    
    public boolean isActive() {
        return this.userStatus == UserStatus.ACTIVE;
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
    
    public enum UserSexType {
        M("남성"),
        F("여성");
        
        private final String description;
        
        UserSexType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
} 