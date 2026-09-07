# Release Process

이 문서는 공모전 제출 규모의 백엔드 저장소에서 branch, version tag, GitHub Release를 운영하는 최소 규칙을 정의한다. 배포 환경 구성과 검증 절차는 [`deployment-plan.md`](deployment-plan.md)와 [`AWS_PRODUCTION_MIGRATION_PLAN.md`](AWS_PRODUCTION_MIGRATION_PLAN.md)에서 다루며, 이 문서는 그 내용을 반복하지 않는다.

## 현재 확인된 상태

2026-09-07 기준으로 확인한 저장소 상태는 다음과 같다.

- GitHub 기본 branch는 `main`이다.
- `dev`와 `feature/*` branch를 사용한 이력이 있지만, `CONTRIBUTING.md`나 별도의 release 규칙은 없다.
- 원격 저장소에는 Git tag와 GitHub Release가 없다.
- 원격 `main`의 GitHub Actions에는 PR review 자동화만 있으며 build, test, container publish, tag, GitHub Release 자동화는 없다.
- 작업 중인 `.github/workflows/container-package.yml`은 아직 Git에 추적되지 않아 GitHub에서 실행 중인 workflow가 아니다. 현재 내용대로 반영한다면 `dev`와 `main`에서 검증하고, `main` push에서만 GHCR image를 commit SHA와 `main` tag로 게시한다.

따라서 아래 내용은 기존 자동화의 설명이 아니라 앞으로 적용할 단순한 운영 규칙이다.

## Branch 역할

| Branch | 역할 | 병합 대상 | Tag / GitHub Release |
| --- | --- | --- | --- |
| `feature/*` | 한 가지 기능 또는 수정 작업 | PR을 통해 `dev` | 만들지 않는다 |
| `dev` | 기능 통합, 자동 테스트, 배포 전 검증 | 검증 후 PR을 통해 `main` | 만들지 않는다 |
| `main` | 의도적으로 배포·제출할 수 있는 기준선 | 직접 작업하지 않고 검증된 변경을 병합 | 실제 release 시점에만 만든다 |
| `hotfix/*` | 공개된 `main`의 긴급 수정 | PR을 통해 `main`, 이후 `dev`에도 반영 | PATCH version release를 만든다 |

별도의 `release/*` branch와 복잡한 GitFlow는 사용하지 않는다.

## Tag와 GitHub Release 생성 시점

`feature/*`를 `dev`에 병합하거나 `dev`에 push할 때는 tag 또는 GitHub Release를 만들지 않는다. 이 단계의 commit은 계속 바뀌는 통합 후보라서 version으로 장기 보존할 기준점이 아니며, commit SHA만으로도 정확히 식별할 수 있다.

`dev`를 `main`에 병합했다고 해서 매번 release를 만들 필요도 없다. 아래 조건을 모두 만족하고 실제 배포, 팀 데모, 심사 제출처럼 보존할 기준점이 생겼을 때 `main`의 해당 commit에 annotated tag를 생성한다.

1. `main`에 필요한 변경이 모두 반영됐다.
2. 자동 테스트와 container build가 성공했다.
3. production 설정 및 DB 변경 사항을 검토했다.
4. health와 대표 기능 smoke test를 통과했다.
5. 되돌릴 직전 정상 commit 또는 image SHA를 기록했다.

GitHub Release는 version tag를 사람이 읽을 수 있는 release note와 함께 공유하기 위한 기록이다. 이 프로젝트에서는 공식 배포·데모·제출에 사용하는 version tag마다 GitHub Release를 하나씩 만드는 것을 권장한다.

## Version 번호 규칙

첫 안정 제출 전에는 단순화한 Semantic Versioning을 사용한다.

- `v0.1.0`: 처음으로 운영 배포 또는 팀 검증이 가능한 기준선
- `v0.2.0`: 사용자에게 보이는 기능, API 계약, DB schema 등 의미 있는 기능 묶음이 추가된 release
- `v0.1.1`: 같은 기능 범위에서 오류, 설정, 보안 문제를 수정한 release
- `v1.0.0`: 공모전 최종 제출본처럼 팀이 첫 안정 version으로 선언한 기준선

문서 오탈자처럼 배포 산출물에 영향이 없는 변경만으로는 새 version을 만들지 않아도 된다. 반대로 이미 공개한 API나 DB schema의 호환성을 깨는 변경은 `0.x` 기간에는 다음 MINOR version으로 올리고 release note에 명확히 기록한다. 공모전 기간에는 MAJOR version의 세밀한 호환성 규칙까지 운영할 필요가 없다.

### Prerelease tag

현재 규모에서는 `dev-*` tag나 매 병합마다 만드는 `v0.1.0-rc.N`이 필요하지 않다. `dev` branch와 commit SHA가 이미 개발 후보를 식별한다.

다만 제출 직전 기능 동결 후 같은 후보를 여러 사람이 검수해야 한다면 `v0.1.0-rc.1` 형식만 예외적으로 사용할 수 있다. 이 경우에도 단순 내부 테스트를 위해 prerelease tag를 만들지는 않는다.

## 현재 CI/CD와의 관계

현재 작업 중인 container workflow는 branch push만 감지하며 Git tag push는 감지하지 않는다. 또한 GitHub Release를 만들거나 `v0.1.0` 같은 version으로 container image를 게시하지 않는다. 그러므로 이 문서의 tag와 GitHub Release 절차는 현재로서는 수동 절차다.

workflow가 반영된 뒤 `main`에 push되면 다음 image가 만들어지는 구조다.

- `ghcr.io/sae-lab/backend:<commit SHA>`: 변경되지 않는 배포·rollback 기준
- `ghcr.io/sae-lab/backend:main`: 최신 `main`을 가리키는 변경 가능한 편의 tag

release note에는 실제 배포한 commit SHA와 image tag를 함께 기록한다. rollback에는 `main`이 아니라 commit SHA image를 사용한다. Git version tag를 추가해도 현재 workflow가 다시 실행되거나 같은 이름의 container tag를 자동 생성하지 않는다는 점에 주의한다.

## 첫 Release 체크리스트

- [ ] `dev`에서 필요한 기능 통합과 테스트를 마쳤다.
- [ ] `dev`에서 `main`으로 보내는 PR을 review하고 병합했다.
- [ ] release 대상이 원격 `main`의 최신 commit인지 확인했다.
- [ ] working tree가 깨끗하고 tag 대상 commit SHA가 확정됐다.
- [ ] Java 17 test, `bootJar`, container build가 성공했다.
- [ ] production Supabase project, schema 적용 방법, backup 또는 복구 방법을 확인했다.
- [ ] production secret과 환경변수가 Git, image, log에 포함되지 않았음을 확인했다.
- [ ] `/livez`, `/readyz`, `/healthz`와 대표 API smoke test를 통과했다.
- [ ] 실제 배포할 immutable container image의 commit SHA를 기록했다.
- [ ] 직전 정상 image SHA와 rollback 명령을 기록했다.
- [ ] 사용자에게 영향을 주는 변경과 알려진 제한을 release note에 작성했다.

## 첫 Tag와 GitHub Release 만들기

아래 명령은 첫 release가 승인되고 `main` 병합이 완료된 뒤 저장소 root에서 직접 실행한다. version은 실제 변경 범위에 맞게 바꾼다.

```bash
git switch main
git pull --ff-only origin main
git status --short
git log -1 --oneline
git tag -a v0.1.0 -m "Release v0.1.0"
git show v0.1.0
git push origin v0.1.0
```

tag를 push한 뒤 GitHub CLI를 사용한다면 다음과 같이 GitHub Release를 생성할 수 있다.

```bash
gh release create v0.1.0 \
  --verify-tag \
  --title "v0.1.0" \
  --generate-notes
```

명령 실행 전 `git log -1`과 `git show v0.1.0`이 의도한 `main` commit을 가리키는지 반드시 확인한다. 게시한 tag를 새 commit으로 강제로 옮기지 않는다.

## Hotfix 최소 절차

1. 최신 `main`에서 `hotfix/<짧은-설명>` branch를 만든다.
2. 수정과 관련 테스트만 포함한 PR을 `main`으로 보낸다.
3. 병합된 `main` commit에 PATCH version tag를 붙이고 GitHub Release를 만든다. 예: `v0.1.0` 다음은 `v0.1.1`.
4. 같은 수정이 빠지지 않도록 `main` 변경을 PR로 `dev`에 반영한다.
5. 새 commit SHA image의 smoke test가 실패하면 기록해 둔 직전 SHA image로 rollback한다.

긴급 수정에도 tag를 임의로 덮어쓰거나 `main`에 직접 force push하지 않는다.
