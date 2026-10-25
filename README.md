# 안심연결 (Ansim Link)

휴대폰이 재난문자를 받으면, 위치 공유에 동의한 가족·지인과 정해진 시간 동안 위치를 실시간으로 공유하고 미리 적어 둔 안심 메시지를 보내는 Android 앱입니다.

- 방재안전 블로그 연재 「Claude Code로 재난 앱 만들기」의 예제 저장소입니다. 코드는 Claude Code로 단계별로 만들고, 단계마다 사용한 지시문은 `docs/prompts/`, 작업 기록은 `docs/devlog/`에 남깁니다.
- 제품 명세와 원칙: [CLAUDE.md](CLAUDE.md)
- 상태: 개발 중 (Google Play 출시 전). 실제 서비스로 쓰려면 위치정보법상 신고, Google Play 정책 심사 등 별도 절차가 필요합니다.
  남은 절차는 [출시 체크리스트](docs/RELEASE_CHECKLIST.md)에 모아 두었고, **아직 하나도 하지 않았습니다.**

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/legal/](docs/legal/) | 이용약관, 개인정보 처리방침, 위치기반서비스 이용약관 (앱 화면과 공개 사이트의 **원본**) |
| [docs/site/](docs/site/) | GitHub Pages 로 공개하는 문서 사이트. 위 원본에서 `./gradlew syncSiteDocuments` 로 만듭니다 |
| [docs/play/](docs/play/) | Play Console 제출 자료 초안 (스토어 등록정보, 데이터 보안, 권한 선언, 앱 콘텐츠, 비공개 테스트 계획) |
| [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) | 출시까지 사람이 해야 하는 일 |
| [functions/README.md](functions/README.md) | Cloud Functions 와 배포 절차 (배포하지 않았습니다) |

> 이 앱은 재난문자 수신을 보장하지 않습니다. 긴급한 상황에서는 119와 재난문자, 지자체 안내를 따르세요.
