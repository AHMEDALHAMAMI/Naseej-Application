package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The user's own profile. Fields left out (null) stay as they are.
 * The investor part is only accepted from investors; changing a verified investor's CR sends it back for review.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateRequest {

    @Size(min = 3, max = 100, message = "Full name must be between 3 and 100 characters")
    private String fullName;

    // Saudi mobile format 9665XXXXXXXX; an empty string removes it
    @Pattern(regexp = "^$|^9665\\d{8}$", message = "Phone must be in the format 9665XXXXXXXX")
    private String phone;

    @Pattern(regexp = "^(ar|en)$", message = "Language must be ar or en")
    private String preferredLanguage;

    // ---------- Investors only ----------

    @Size(max = 120, message = "Company name must be at most 120 characters")
    private String companyName;

    @Pattern(regexp = "^\\d{10}$", message = "Commercial registration number must be 10 digits")
    private String crNumber;

    // Budget range in SAR. Send budgetMin with budgetMax, or with budgetNoMax = true for "and above"
    @PositiveOrZero(message = "Budget can't be negative")
    private BigDecimal budgetMin;

    @PositiveOrZero(message = "Budget can't be negative")
    private BigDecimal budgetMax;

    private Boolean budgetNoMax;

    @Pattern(regexp = "^(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION)(,(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION))*$",
            message = "Investment fields must be a comma-separated list of FOOD, RETAIL, HEALTH, FITNESS, HOME_SERVICES, EDUCATION")
    private String investmentFields;
}
