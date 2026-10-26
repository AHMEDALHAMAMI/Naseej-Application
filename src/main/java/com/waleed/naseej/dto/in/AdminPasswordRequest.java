package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin sets a new password for a member (e.g. the member lost access); the member's sessions end. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPasswordRequest {

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers")
    private String newPassword;
}
