package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectStageRequest {

    @NotBlank(message = "Stage is required")
    private String stage;

    @Size(max = 1000, message = "Note must be at most 1000 characters")
    private String note;
}
