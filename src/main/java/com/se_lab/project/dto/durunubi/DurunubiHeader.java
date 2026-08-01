package com.se_lab.project.dto.durunubi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class DurunubiHeader {

    private String resultCode;

    private String resultMsg;
}