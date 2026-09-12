-- 웨이포인트 사진을 선택 항목으로 바꾸면서 필요해진 변경.
--
-- 엔티티(UserRouteWaypoint.photoUrl)에는 이미 NOT NULL이 없지만,
-- 컬럼이 예전에 NOT NULL로 만들어졌고 ddl-auto=update는 제약을 풀어주지 못한다.
-- 그래서 사진 없이 웨이포인트를 추가하면 다음 오류로 500이 났다:
--
--   ERROR: null value in column "photo_url" of relation "user_route_waypoints"
--   violates not-null constraint
--
-- 제약을 푸는 방향이라 기존 데이터는 그대로 남는다.
-- 운영 DB에는 2026-09-09에 적용했다 (적용 시점 photo_url이 null인 행은 0개였다).

alter table user_route_waypoints
    alter column photo_url drop not null;
