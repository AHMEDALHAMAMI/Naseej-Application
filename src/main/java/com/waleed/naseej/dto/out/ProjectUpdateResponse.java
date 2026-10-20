package com.waleed.naseej.dto.out;

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
public class ProjectUpdateResponse {
    private Integer id;
    private Integer contractId;
    private String stage;
    private String note;
    private LocalDateTime createdAt;
}
