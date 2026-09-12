package com.se_lab.project.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TourImageUrlNormalizerTest {

    @Test
    void upgradesTourApiHttpImageToHttps() {
        String imageUrl = "http://tong.visitkorea.or.kr/cms/resource/38/1581438_image2_1.jpg";

        assertEquals(
                "https://tong.visitkorea.or.kr/cms/resource/38/1581438_image2_1.jpg",
                TourImageUrlNormalizer.normalize(imageUrl)
        );
    }

    @Test
    void keepsExistingHttpsImage() {
        String imageUrl = "https://tong.visitkorea.or.kr/cms/resource/image.jpg";

        assertEquals(imageUrl, TourImageUrlNormalizer.normalize(imageUrl));
    }

    @Test
    void doesNotRewriteOtherHttpHosts() {
        String imageUrl = "http://example.com/image.jpg";

        assertEquals(imageUrl, TourImageUrlNormalizer.normalize(imageUrl));
    }

    @Test
    void keepsEmptyImageUrl() {
        assertEquals("", TourImageUrlNormalizer.normalize(""));
    }
}
