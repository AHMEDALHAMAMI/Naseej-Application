package com.waleed.naseej.entity;
import com.waleed.naseej.enums.OtpPurpose;
import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

//    @Column(nullable = false)
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
    @Builder.Default
    private Role role = Role.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "neighborhood_id")
    private Neighborhood neighborhood;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean whatsappOptIn = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(unique = true)
    private String refreshTokenHash;

    private LocalDateTime refreshTokenExpiresAt;

    private String otpHash;

    @Enumerated(EnumType.STRING)
    private OtpPurpose otpPurpose;

    private LocalDateTime otpExpiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer otpAttempts = 0;

    // ---------- Location (from the browser at sign-up, used for the voting radius) ----------

    private Double latitude;

    private Double longitude;

    // Last time the resident added an address or moved one beyond a small fix (one change every 30 days)
    private LocalDateTime lastAddressChangeAt;

    // ---------- Investor profile (only for role INVESTOR) ----------

    // Added later, when the investor first wants to unlock; null until then
    @Column(length = 10)
    private String crNumber;

    // UP_TO_300K, FROM_300K_TO_700K, ABOVE_700K or CUSTOM (then budgetMin / budgetMax hold the range)
    @Column(length = 20)
    private String investmentBudget;

    // Comma-separated fields, e.g. "FOOD,HEALTH"
    @Column(length = 200)
    private String investmentFields;

    // A field the investor typed in their own words, e.g. "car wash"
    @Column(length = 200)
    private String investmentFieldsOther;

    // The idea categories the AI matched to investmentFieldsOther, comma-separated (empty when the AI is off)
    @Column(length = 300)
    private String investmentFieldsOtherCategories;

    // Neighborhood ids the investor wants to invest in, comma-separated
    @Column(length = 500)
    private String investmentAreas;

    // Null for non-investors. PENDING with no CR means "not submitted yet"; with a CR it waits for the admin
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InvestorStatus investorStatus;

    private LocalDateTime investorReviewedAt;

    // Budget range in SAR chosen by the investor: the CUSTOM range from the investor preferences, or the range
    // from the account page (investmentBudget then keeps the closest bucket for older screens). Either end may be empty
    @Column(precision = 12, scale = 2)
    private BigDecimal budgetMin;

    // Null means "and above"
    @Column(precision = 12, scale = 2)
    private BigDecimal budgetMax;

    @Column(length = 120)
    private String companyName;

    // ---------- Preferences ----------

    // "ar" or "en": the language of the website and of AI text written for this user
    @Column(length = 2)
    private String preferredLanguage;
}
