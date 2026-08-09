package com.se_lab.project.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrailPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private Integer sequence;


    private Double lat;

    private Double lng;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trail_id")
    @Setter
    @JsonIgnore
    private Trail trail;

}