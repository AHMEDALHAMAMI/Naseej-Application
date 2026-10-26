package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** The latest notifications (newest first) and how many are still unread, for the bell in the header. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationListResponse {
    private Long unread;
    private List<NotificationResponse> items;
}
