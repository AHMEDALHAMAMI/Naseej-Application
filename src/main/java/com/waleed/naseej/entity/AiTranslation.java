package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AI text (a check summary, a business model, a report, a price explanation) translated once into the
 * reader's other language and reused. sourceHash is the hash of the original text: when the original
 * changes (the author edits the business model, say) the translation is made again.
 */
@Entity
@Table(name = "ai_translations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source_type", "source_id", "lang"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Integer sourceId;

    @Column(nullable = false, length = 5)
    private String lang;

    // The translated fields as JSON ({"summary": "..."})
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
