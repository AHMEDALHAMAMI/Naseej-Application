package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.IdeaStatus;
import java.time.LocalDateTime;
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
public class IdeaResponse {
    private Integer id;
    private Integer ownerId;
    private Integer neighborhoodId;
    private String title;
    private String description;
    private String category;
    private IdeaStatus status;
    private Integer membershipThreshold;
    private LocalDateTime publishedAt;
    private LocalDateTime confirmedAt;
}
