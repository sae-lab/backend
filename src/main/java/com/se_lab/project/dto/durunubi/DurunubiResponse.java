package com.se_lab.project.dto.durunubi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class DurunubiResponse {

    private DurunubiHeader header;

    private DurunubiBody body;

}