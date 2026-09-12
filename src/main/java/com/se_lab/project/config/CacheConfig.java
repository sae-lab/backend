package com.se_lab.project.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String HOME_RECOMMENDATION_CANDIDATES = "homeRecommendationCandidates";

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.home-recommendation-candidates.ttl:30m}") Duration homeRecommendationCandidatesTtl
    ) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(HOME_RECOMMENDATION_CANDIDATES);

        cacheManager.registerCustomCache(
                HOME_RECOMMENDATION_CANDIDATES,
                Caffeine.newBuilder()
                        .maximumSize(1)
                        .expireAfterWrite(homeRecommendationCandidatesTtl)
                        .build()
        );

        return cacheManager;
    }
}
