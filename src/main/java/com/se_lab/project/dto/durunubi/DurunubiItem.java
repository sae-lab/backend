package com.se_lab.project.dto.durunubi;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class DurunubiItem {

    private String routeIdx;
    private String crsIdx;
    private String crsKorNm;
    private String crsDstnc;
    private String crsTotlRqrmHour;
    private String crsLevel;
    private String crsCycle;
    private String crsContents;
    private String crsSummary;
    private String crsTourInfo;
    private String travelerinfo;
    private String sigun;
    private String brdDiv;
    private String gpxpath;
}