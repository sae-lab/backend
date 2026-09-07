## 변경 내용

- <!-- 무엇을 왜 변경했는지 적어주세요. -->

## 검증

- [ ] 관련 테스트를 실행했다.
- [ ] secret, `.env`, credential이 변경 내용과 로그에 포함되지 않았다.

## `main` 대상 PR인 경우에만

> [`docs/release-process.md`](https://github.com/sae-lab/backend/blob/main/docs/release-process.md)의 체크리스트를 확인한다. `main` 병합과 version release는 같은 의미가 아니므로 이번 병합의 release 여부를 명시한다.

- [ ] source가 검증된 `dev`이고 target이 `main`이다.
- [ ] Java 17 test, `bootJar`, container build 결과를 확인했다.
- [ ] production 설정, DB schema, health 및 rollback 영향을 확인했다.
- [ ] 이번 병합의 release 여부와 예정 version을 아래에 기록했다.

Release 여부 / 예정 version: `release 아님` 또는 `v0.0.0`
