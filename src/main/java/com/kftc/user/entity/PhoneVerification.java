package com.kftc.user.entity;

import com.kftc.common.domain.DateTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kftc_phone_verifications")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PhoneVerification extends DateTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "kftc_phone_verification_seq")
    @SequenceGenerator(name = "kftc_phone_verification_seq", sequenceName = "kftc_phone_verification_sequence", allocationSize = 1)
    private Long id;
    
    @Column(nullable = false, length = 11)
    private String phoneNumber;
    
    @Column(nullable = false, length = 6)
    private String verificationCode;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;
    
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public void markAsVerified() {
        this.verified = true;
    }
} 