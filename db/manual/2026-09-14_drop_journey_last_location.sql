-- 여정의 마지막 위치 칸 삭제.
--
-- 예전에는 앱이 20초마다 GPS 좌표를 서버로 보내고, 서버가 마지막 좌표를 저장해
-- 걸은 거리와 스팟 도착을 판정했다. 개인 위치를 서버로 전송·저장하는 구조라
-- 위치기반서비스사업자 신고 대상이어서(sae-lab/backend#57), 판정을 앱으로 옮겼다.
-- 엔티티(RouteJourney)에서 필드를 지워도 ddl-auto=update는 컬럼을 지우지 않고,
-- 기존 행에 남은 좌표도 그대로 남는다.
--
-- 적용 순서: 새 백엔드를 배포한 뒤에 실행한다. 먼저 실행하면 이전 버전 서버가
-- 없는 컬럼을 조회하다 여정 API가 500으로 실패한다.
-- 아직 운영 DB에 적용하지 않았다.

alter table route_journeys
    drop column if exists last_lat,
    drop column if exists last_lng,
    drop column if exists last_ping_at;
