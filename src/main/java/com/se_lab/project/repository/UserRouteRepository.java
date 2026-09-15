package com.se_lab.project.repository;

import com.se_lab.project.entity.User;
import com.se_lab.project.entity.UserRoute;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRouteRepository extends JpaRepository<UserRoute, Long> {

    // 목록 화면은 게시물마다 작성자와 웨이포인트(표지·스팟 수)를 쓴다. 따로 불러오면
    // 게시물 수만큼 쿼리가 더 나가므로 목록을 가져올 때 함께 가져온다.

    @EntityGraph(attributePaths = {"author", "waypoints"})
    List<UserRoute> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"author", "waypoints"})
    List<UserRoute> findByAuthorOrderByCreatedAtDesc(User author);

    @EntityGraph(attributePaths = {"author", "waypoints"})
    List<UserRoute> findAllByRouteTypeOrderByCreatedAtDesc(String routeType);

    @EntityGraph(attributePaths = {"author", "waypoints"})
    List<UserRoute> findByAuthorAndRouteTypeOrderByCreatedAtDesc(User author, String routeType);
}
