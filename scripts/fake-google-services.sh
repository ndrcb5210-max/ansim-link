#!/usr/bin/env bash
#
# CI·로컬 빌드용 가짜 app/google-services.json 을 만든다.
#
# 왜 필요한가
#   google-services 플러그인은 app/google-services.json 이 있어야 동작한다.
#   진짜 파일은 저장소에 넣지 않으므로(CLAUDE.md 원칙 6), 빌드만 통과시킬 가짜 값을 써 넣는다.
#   값이 가짜이므로 Firebase 서버에는 연결되지 않는다. 앱은 project_id 를 보고
#   '서버 설정이 필요합니다' 안내 화면을 보여 준다(ServerConfig.kt 참고).
#
# 쓰는 법
#   ./scripts/fake-google-services.sh           # 파일이 이미 있으면 그대로 둔다
#   ./scripts/fake-google-services.sh --force   # 덮어쓴다
#
# 진짜 파일을 쓰려면 Firebase 콘솔에서 내려받아 app/google-services.json 에 두면 된다.
# (.gitignore 에 들어 있어 커밋되지 않는다.)

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target="${repo_root}/app/google-services.json"

force="no"
if [ "${1:-}" = "--force" ]; then
  force="yes"
fi

if [ -f "${target}" ] && [ "${force}" = "no" ]; then
  echo "app/google-services.json 이 이미 있습니다. 그대로 둡니다(덮어쓰려면 --force)."
  exit 0
fi

# 패키지 이름은 app/build.gradle.kts 의 applicationId 와 같아야 플러그인이 설정을 찾는다.
package_name="io.github.ndrcb5210max.ansimlink"

# 아래 값은 전부 가짜다. 형식만 맞춘 자리표시자이며 어떤 Firebase 프로젝트와도 연결되지 않는다.
# API 키 자리에 실제 키처럼 보이는 문자열(AIza...)을 쓰면 비밀 검사 도구가 오탐하므로 일부러 피한다.
cat > "${target}" <<'JSON'
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "ansim-link-placeholder",
    "storage_bucket": "ansim-link-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "io.github.ndrcb5210max.ansimlink"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "FAKE-API-KEY-FOR-BUILD-ONLY-DO-NOT-USE"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
JSON

# 패키지 이름이 바뀌어도 스크립트가 따라가도록 한 번 더 확인한다.
if ! grep -q "\"${package_name}\"" "${target}"; then
  echo "경고: 스크립트의 package_name(${package_name})이 파일 내용과 다릅니다." >&2
fi

echo "가짜 app/google-services.json 을 만들었습니다 (project_id=ansim-link-placeholder)."
echo "이 파일은 .gitignore 에 들어 있어 커밋되지 않습니다."
