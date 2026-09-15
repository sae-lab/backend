package com.se_lab.project.repository;

import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteScrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRouteScrapRepository extends JpaRepository<UserRouteScrap, Long> {
    long countByRoute(UserRoute route);

    /// 목록 화면용. 게시물마다 count 쿼리를 따로 날리지 않도록 한 번에 센다. [게시물 id, 개수]
    @Query("select s.route.id, count(s) from UserRouteScrap s where s.route in :routes group by s.route.id")
    List<Object[]> countByRoutes(@Param("routes") Collection<UserRoute> routes);

    /// 목록 화면용. 이 사용자가 저장한 게시물 id만 한 번에 가져온다.
    @Query("select s.route.id from UserRouteScrap s where s.user = :user and s.route in :routes")
    List<Long> findRouteIdsByUserAndRoutes(@Param("user") User user, @Param("routes") Collection<UserRoute> routes);

    Optional<UserRouteScrap> findByUserAndRoute(User user, UserRoute route);
    boolean existsByUserAndRoute(User user, UserRoute route);
    List<UserRouteScrap> findByUserOrderByScrapedAtDesc(User user);
    void deleteByRoute(UserRoute route);
}
