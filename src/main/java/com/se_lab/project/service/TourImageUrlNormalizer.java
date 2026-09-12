package com.se_lab.project.service;

final class TourImageUrlNormalizer {

    private static final String HTTP_PREFIX = "http://tong.visitkorea.or.kr/";
    private static final String HTTPS_PREFIX = "https://tong.visitkorea.or.kr/";

    private TourImageUrlNormalizer() {
    }

    static String normalize(String imageUrl) {
        if (imageUrl.startsWith(HTTP_PREFIX)) {
            return HTTPS_PREFIX + imageUrl.substring(HTTP_PREFIX.length());
        }
        return imageUrl;
    }
}
