package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Integer id;
    private String type;
    private Integer ideaId;
    private String ideaTitle;
    private String detail;
    private String link;
    private Boolean read;
    private LocalDateTime createdAt;
}
