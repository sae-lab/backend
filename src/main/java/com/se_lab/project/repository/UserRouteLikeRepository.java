package com.se_lab.project.repository;

import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRouteLikeRepository extends JpaRepository<UserRouteLike, Long> {
    long countByRoute(UserRoute route);

    /// 목록 화면용. 게시물마다 count 쿼리를 따로 날리지 않도록 한 번에 센다. [게시물 id, 개수]
    @Query("select l.route.id, count(l) from UserRouteLike l where l.route in :routes group by l.route.id")
    List<Object[]> countByRoutes(@Param("routes") Collection<UserRoute> routes);

    /// 목록 화면용. 이 사용자가 좋아요 누른 게시물 id만 한 번에 가져온다.
    @Query("select l.route.id from UserRouteLike l where l.user = :user and l.route in :routes")
    List<Long> findRouteIdsByUserAndRoutes(@Param("user") User user, @Param("routes") Collection<UserRoute> routes);

    Optional<UserRouteLike> findByUserAndRoute(User user, UserRoute route);
    boolean existsByUserAndRoute(User user, UserRoute route);
    void deleteByRoute(UserRoute route);
}
