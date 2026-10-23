package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "neighborhoods",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_neighborhood_name_city",
                columnNames = {"name", "city"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Neighborhood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    private Double latitude;

    private Double longitude;
}
