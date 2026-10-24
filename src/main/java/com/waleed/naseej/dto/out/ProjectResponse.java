package com.waleed.naseej.dto.out;

import com.waleed.naseej.enums.AccessType;
import com.waleed.naseej.enums.ContractStatus;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An adopted idea as seen on "My projects" by its investor or its founder. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {

    private Integer contractId;
    private String contractNumber;
    private Integer ideaId;
    private String ideaTitle;
    private String category;
    private String neighborhoodName;
    private String city;
    // INVESTOR or FOUNDER
    private String myRole;
    private AccessType accessType;
    private String investorName;
    private String founderName;
    private ContractStatus contractStatus;
    private LocalDateTime activatedAt;
    private List<String> stages;
    private String currentStage;
    private Integer stageIndex;
    private Integer progressPercent;
    private List<ProjectUpdateResponse> updates;
    private Boolean canPostUpdate;
    private LocalDateTime nextUpdateAllowedAt;
}
