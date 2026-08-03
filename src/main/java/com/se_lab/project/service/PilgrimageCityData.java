package com.se_lab.project.service;

import java.util.List;

/**
 * 강원특별자치도 시군을 지리적으로 이어지는 순서(벨트)로 정의한 정적 데이터.
 * 실제 도보 경로 API가 없어, 이 순서를 기반으로 인접한 도시끼리만 순례길을 자동 생성한다.
 */
public final class PilgrimageCityData {

    private PilgrimageCityData() {
    }

    public static final List<GangwonCity> COASTAL_BELT = List.of(
            new GangwonCity("고성", 38.3800, 128.4674),
            new GangwonCity("속초", 38.2070, 128.5918),
            new GangwonCity("양양", 38.0755, 128.6189),
            new GangwonCity("강릉", 37.7519, 128.8761),
            new GangwonCity("동해", 37.5247, 129.1143),
            new GangwonCity("삼척", 37.4500, 129.1653)
    );

    public static final List<GangwonCity> INLAND_BELT = List.of(
            new GangwonCity("춘천", 37.8813, 127.7298),
            new GangwonCity("홍천", 37.6971, 127.8887),
            new GangwonCity("횡성", 37.4917, 127.9853),
            new GangwonCity("평창", 37.3705, 128.3900),
            new GangwonCity("정선", 37.3806, 128.6608),
            new GangwonCity("태백", 37.1640, 128.9856)
    );

    public static final List<GangwonCity> BORDER_BELT = List.of(
            new GangwonCity("철원", 38.1465, 127.3134),
            new GangwonCity("화천", 38.1064, 127.7083),
            new GangwonCity("양구", 38.1057, 127.9898),
            new GangwonCity("인제", 38.0695, 128.1707)
    );

    public static final List<List<GangwonCity>> ALL_BELTS = List.of(COASTAL_BELT, INLAND_BELT, BORDER_BELT);
}
