-- 서버 여정 추적 기능 제거에 따른 테이블 삭제.
--
-- 위치정보지원센터 답변(2026-09-15): 이용자 위치를 서버로 보내 이동거리·지점 도달을
-- 판단하거나, 위치를 게시물·여행 기록과 연계해 저장하면 위치기반서비스사업 신고 대상에 해당할 수 있다.
-- 사업자등록이 없는 팀이라 신고할 수 없으므로, 여정(스탬프·걸은 거리·시간) 기록을
-- 서버가 아니라 사용자 브라우저에만 저장하도록 바꿨다 (sae-lab/backend#57).
--
-- 엔티티(RouteJourney, RouteJourneyCheckpoint)를 지워도 ddl-auto=update는 테이블을 지우지 않는다.
-- 테이블에는 예전 방식으로 받은 마지막 위치 좌표(last_lat, last_lng)와 스팟 도착 시각이 남아 있다.
--
-- 적용 순서: 새 백엔드를 배포한 뒤에 실행한다. 먼저 실행하면 이전 버전 서버의 여정 API가 500으로 실패한다.
-- 아직 운영 DB에 적용하지 않았다.

drop table if exists route_journey_checkpoints;
drop table if exists route_journeys;
