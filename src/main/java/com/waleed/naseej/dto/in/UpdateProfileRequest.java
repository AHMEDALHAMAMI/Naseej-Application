package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.Max;
import com.waleed.naseej.service.PhoneNumbers;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The logged-in user edits their profile. Send only what changes; empty fields are left as they are.
 * Email, password and role have their own flows (OTP, reset-password, select-role).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(min = 3, max = 100, message = "Full name must be between 3 and 100 characters")
    private String fullName;

    @Pattern(regexp = PhoneNumbers.REGEX, message = PhoneNumbers.MESSAGE)
    private String phone;

    private Boolean whatsappOptIn;

    // Residents only
    private Integer neighborhoodId;

    // ---------- Investors only. A new CR number sends the account back to admin review ----------
    @Pattern(regexp = "^7\\d{9}$", message = "Commercial registration number must be 10 digits starting with 7")
    private String crNumber;

    // An empty string clears the choice. CUSTOM uses budgetMin / budgetMax
    @Pattern(regexp = "^$|^(UP_TO_300K|FROM_300K_TO_700K|ABOVE_700K|CUSTOM)$",
            message = "Investment budget must be UP_TO_300K, FROM_300K_TO_700K, ABOVE_700K or CUSTOM")
    private String investmentBudget;

    // Custom budget range in SAR (with investmentBudget = CUSTOM); one end may be left empty
    @PositiveOrZero(message = "Budget can't be negative")
    @Max(value = 1_000_000_000L, message = "Budget is too large")
    private Long budgetMin;

    @PositiveOrZero(message = "Budget can't be negative")
    @Max(value = 1_000_000_000L, message = "Budget is too large")
    private Long budgetMax;

    // An empty string clears the choice
    @Pattern(regexp = "^$|^(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION)(,(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION))*$",
            message = "Investment fields must be a comma-separated list of FOOD, RETAIL, HEALTH, FITNESS, HOME_SERVICES, EDUCATION")
    private String investmentFields;

    // A field in the investor's own words; an empty string clears it
    @Size(max = 200, message = "The other field must be at most 200 characters")
    private String investmentFieldsOther;

    // Neighborhood ids, comma-separated; an empty string clears them
    @Pattern(regexp = "^$|^\\d+(,\\d+){0,29}$", message = "Investment areas must be up to 30 neighborhood ids separated by commas")
    private String investmentAreas;
}
