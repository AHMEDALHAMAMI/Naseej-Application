package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The founder edits a published idea. Send the fields to change; the category and the place stay as screened.
 * The title is locked once neighbors have voted for it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateIdeaRequest {

    @Size(min = 3, max = 120, message = "Title must be between 3 and 120 characters")
    private String title;

    @Size(min = 1, max = 2000, message = "Description must be between 1 and 2000 characters")
    private String description;
}
