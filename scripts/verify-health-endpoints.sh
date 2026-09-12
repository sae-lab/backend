#!/usr/bin/env bash

set -euo pipefail

base_url="${1:-http://127.0.0.1:8080}"
expected_state="${2:-up}"

check_endpoint() {
    local path="$1"
    local expected_code="$2"
    local expected_status="$3"
    local response
    local body
    local code

    response="$(curl --silent --show-error \
        --connect-timeout 3 \
        --max-time 5 \
        --write-out $'\n%{http_code}' \
        "${base_url}${path}")"
    body="${response%$'\n'*}"
    code="${response##*$'\n'}"

    if [[ "$code" != "$expected_code" ]] || [[ "$body" != *"\"status\":\"${expected_status}\""* ]]; then
        echo "FAIL ${path}: expected HTTP ${expected_code}/${expected_status}, got HTTP ${code} ${body}" >&2
        return 1
    fi

    echo "PASS ${path}: HTTP ${code} ${body}"
}

case "$expected_state" in
    up)
        check_endpoint /livez 200 UP
        check_endpoint /readyz 200 UP
        check_endpoint /healthz 200 UP
        ;;
    db-down)
        # 테스트/스테이징 DB 연결을 실제로 차단한 뒤 실행한다.
        # 이 스크립트 자체는 DB나 network 설정을 변경하지 않는다.
        check_endpoint /livez 200 UP
        check_endpoint /readyz 503 DOWN
        check_endpoint /healthz 503 DOWN
        ;;
    *)
        echo "usage: bash scripts/verify-health-endpoints.sh [base-url] [up|db-down]" >&2
        exit 2
        ;;
esac
