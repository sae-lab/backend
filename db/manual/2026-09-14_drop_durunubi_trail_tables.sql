-- 두루누비 코스 기능을 제거하면서 남은 빈 테이블 정리.
--
-- 두루누비 코스와 GPX 좌표를 DB에 저장해 쓰던 구조였는데, 한국관광공사 규정상
-- 실시간 호출이 가능한 데이터는 로컬에 저장할 수 없어 기능을 뺐다 (sae-lab/backend#58).
-- 엔티티(Trail, TrailPoint)를 지워도 ddl-auto=update는 테이블을 지우지 않는다.
--
-- 두 테이블의 데이터는 2026-09-14에 이미 삭제했다 (trail 103행, trail_point 150,832행).
-- 아직 운영 DB에 적용하지 않았다. 팀원 브랜치(feature/LAB-12-route-ui)가 이 엔티티를
-- 쓰고 있으면 ddl-auto가 다시 만들 수 있으니, 그 브랜치 정리 후에 적용한다.

drop table if exists trail_point;
drop table if exists trail;
