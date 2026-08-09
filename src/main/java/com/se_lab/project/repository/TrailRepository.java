package com.se_lab.project.repository;

import com.se_lab.project.entity.Trail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrailRepository extends JpaRepository<Trail, Long> {


    @Query("""
            select distinct t
            from Trail t
            join fetch t.points
            """)
    List<Trail> findAllWithPoints();


    @Query("""
            select t.courseId
            from Trail t
            """)
    List<String> findAllCourseIds();

    @Query("""
        select t
        from Trail t
        """)
    List<Trail> findAllTrails();

    @Query(value = """
        SELECT
            t.id,
            t.course_name,
            t.region,
            t.distance,
            MIN(
                6371 * 2 * ASIN(
                    SQRT(
                        POWER(SIN(RADIANS(tp.lat - :userLat) / 2), 2)
                        +
                        COS(RADIANS(:userLat))
                        * COS(RADIANS(tp.lat))
                        * POWER(SIN(RADIANS(tp.lng - :userLng) / 2), 2)
                    )
                )
            ) AS nearest_distance_km
        FROM trail t
        JOIN trail_point tp
            ON tp.trail_id = t.id
        GROUP BY
            t.id,
            t.course_name,
            t.region,
            t.distance
        ORDER BY nearest_distance_km
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findNearbyTrailDistances(
            @Param("userLat") double userLat,
            @Param("userLng") double userLng,
            @Param("limit") int limit
    );

    @Query("""
        select distinct t
        from Trail t
        join fetch t.points
        where t.id in :ids
        """)
    List<Trail> findAllWithPointsByIds(
            @Param("ids") List<Long> ids
    );
}