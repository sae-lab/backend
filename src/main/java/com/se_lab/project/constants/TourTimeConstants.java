package com.se_lab.project.constants;

import java.util.Map;

public final class TourTimeConstants {

    public static final int DEFAULT_STAY_TIME = 60;

    private static final Map<String, Integer> STAY_TIMES = Map.of(
            TourApiConstants.CONTENT_TYPE_TOURIST_ATTRACTION, 90,
            TourApiConstants.CONTENT_TYPE_CULTURE, 60,
            TourApiConstants.CONTENT_TYPE_FESTIVAL, 120,
            TourApiConstants.CONTENT_TYPE_LEPORTS, 120,
            TourApiConstants.CONTENT_TYPE_LODGING, 0,
            TourApiConstants.CONTENT_TYPE_SHOPPING, 45,
            TourApiConstants.CONTENT_TYPE_FOOD, 50
    );

    private TourTimeConstants() {
    }

    public static int getStayTime(String contentTypeId) {
        if (contentTypeId == null || contentTypeId.isBlank()) {
            return DEFAULT_STAY_TIME;
        }
        return STAY_TIMES.getOrDefault(contentTypeId, DEFAULT_STAY_TIME);
    }
}

