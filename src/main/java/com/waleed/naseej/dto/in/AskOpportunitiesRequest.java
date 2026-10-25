package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The investor's question in their own words, e.g. "صيدلية في الرياض بأقل من 600 ألف، جاهزة للفتح". */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AskOpportunitiesRequest {

    @NotBlank(message = "Write what you're looking for")
    @Size(min = 3, max = 300, message = "The question must be between 3 and 300 characters")
    private String question;
}
