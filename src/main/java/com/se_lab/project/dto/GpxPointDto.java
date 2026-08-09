package com.se_lab.project.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpxPointDto {

    private Double lat;

    private Double lng;

    private Double elevation;
}