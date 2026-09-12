package com.se_lab.project.repository;

import com.se_lab.project.entity.UserRoute;
import com.se_lab.project.entity.UserRouteComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRouteCommentRepository extends JpaRepository<UserRouteComment, Long> {
    List<UserRouteComment> findByRouteAndParentIsNullOrderByCreatedAtAsc(UserRoute route);
    List<UserRouteComment> findByParentOrderByCreatedAtAsc(UserRouteComment parent);
    // 게시글 상세 조회에서 최상위 댓글마다 대댓글을 따로 쿼리하지 않도록, 한 번에 전부 가져와
    // 서비스 계층에서 부모 id로 묶어 쓴다.
    List<UserRouteComment> findByRouteOrderByCreatedAtAsc(UserRoute route);
    long countByRoute(UserRoute route);

    // 게시글 전체 삭제 시 사용. 대댓글이 부모 댓글을 FK로 참조하므로, 반드시
    // 대댓글(parent != null)을 먼저 지운 뒤 최상위 댓글을 지워야 제약조건 위반이 없다.
    void deleteByRouteAndParentIsNotNull(UserRoute route);
    void deleteByRouteAndParentIsNull(UserRoute route);
    void deleteByParent(UserRouteComment parent);
}
