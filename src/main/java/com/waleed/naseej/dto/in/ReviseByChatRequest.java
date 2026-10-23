package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviseByChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message can't be longer than 1000 characters")
    private String message;
}
