package com.waleed.naseej.entity;

import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider;

    @Column(unique = true)
    private String googleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "neighborhood_id")
    private Neighborhood neighborhood;

    @Column(nullable = false)
    private Boolean emailVerified = false;

    @Column(nullable = false)
    private Boolean whatsappOptIn = false;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(unique = true)
    private String refreshTokenHash;

    private LocalDateTime refreshTokenExpiresAt;

    private String otpHash;

    private String otpPurpose;

    private LocalDateTime otpExpiresAt;

    @Column(nullable = false)
    private Integer otpAttempts = 0;
}