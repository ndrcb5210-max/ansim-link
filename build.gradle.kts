// 루트 프로젝트에서는 플러그인을 적용하지 않고 버전만 고정한다(각 모듈에서 적용).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.google.services) apply false
}

// ─────────────────────────────────────────────────────────────────────────────
// 공개용 문서 사이트(docs/site) 만들기 — 6단계
// ─────────────────────────────────────────────────────────────────────────────
//
// 앱 안에서 보여 주는 약관은 docs/legal 을 원본으로 삼아 res/raw 로 복사한다
// (app/build.gradle.kts 의 copyLegalDocuments). GitHub Pages 로 공개하는 쪽도 **같은 원본**을
// 써야 한다. 같은 글을 두 군데 손으로 두면 반드시 어긋나는데, 여기서 어긋나는 것은 단순한
// 버그가 아니다 — 앱이 보여 준 처리방침과 공개된 처리방침이 다른 상태가 된다.
//
// 그래서 docs/site 의 네 페이지는 이 태스크가 만든다. 사람은 원본만 고친다.
// 어긋난 채로 커밋되는 것은 단위 테스트(SiteDocumentsTest)가 막는다 — 그 테스트는
// `./gradlew testDebugUnitTest` 에 들어 있어서 CI 와 단계별 검사에서 같이 돌아간다.
class SiteDocument(
    /** 저장소 루트 기준 원본 경로. */
    val source: String,
    /** 저장소 루트 기준 사이트 페이지 경로. */
    val target: String,
    val title: String,
    val permalink: String,
)

val siteDocuments = listOf(
    SiteDocument(
        source = "docs/legal/privacy-policy.md",
        target = "docs/site/privacy-policy.md",
        title = "개인정보 처리방침",
        permalink = "/privacy-policy/",
    ),
    SiteDocument(
        source = "docs/legal/location-terms.md",
        target = "docs/site/location-terms.md",
        title = "위치기반서비스 이용약관",
        permalink = "/location-terms/",
    ),
    SiteDocument(
        source = "docs/legal/terms-of-service.md",
        target = "docs/site/terms-of-service.md",
        title = "이용약관",
        permalink = "/terms-of-service/",
    ),
    SiteDocument(
        source = "docs/account-deletion.md",
        target = "docs/site/account-deletion.md",
        title = "계정 및 데이터 삭제 요청",
        permalink = "/account-deletion/",
    ),
)

// 사이트 페이지 한 장을 만든다. 앞머리(front matter) + '만들어진 파일' 표시 + 원본 전문.
//
// 줄바꿈은 LF 로 통일한다. .gitattributes 의 `* text=auto` 때문에 윈도우 작업 폴더의
// 원본은 CRLF 일 수 있는데, git 이 비교할 때 양쪽을 LF 로 정규화하므로 LF 로 써도
// '변경됨'으로 보이지 않는다. 테스트도 양쪽을 정규화해서 비교한다.
fun renderSitePage(document: SiteDocument, sourceText: String): String = buildString {
    append("---\n")
    append("layout: page\n")
    append("title: " + document.title + "\n")
    append("permalink: " + document.permalink + "\n")
    append("---\n")
    append("\n")
    append("<!--\n")
    append("  이 파일은 " + document.source + " 에서 만든 사본이다.\n")
    append("  직접 고치지 말고 원본을 고친 뒤 `./gradlew syncSiteDocuments` 를 돌린다.\n")
    append("  어긋나면 단위 테스트 SiteDocumentsTest 가 실패한다.\n")
    append("-->\n")
    append("\n")
    append(sourceText.replace("\r\n", "\n").trimEnd())
    append("\n")
}

val repositoryRoot = layout.projectDirectory

tasks.register("syncSiteDocuments") {
    group = "documentation"
    description = "docs/legal 과 docs/account-deletion.md 를 docs/site 의 공개 페이지로 복사한다."

    val documents = siteDocuments
    val root = repositoryRoot
    inputs.files(documents.map { root.file(it.source) })
    outputs.files(documents.map { root.file(it.target) })

    doLast {
        documents.forEach { document ->
            val sourceFile = root.file(document.source).asFile
            val targetFile = root.file(document.target).asFile
            targetFile.parentFile.mkdirs()
            targetFile.writeText(renderSitePage(document, sourceFile.readText()))
            logger.lifecycle("[ansim-link] " + document.target + " ← " + document.source)
        }
    }
}
