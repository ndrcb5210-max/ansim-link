package io.github.ndrcb5210max.ansimlink.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 공개용 문서 사이트(`docs/site`)가 앱이 보여 주는 원본과 같은 내용인지 본다.
 *
 * 왜 단위 테스트로 두는가. 앱 화면은 `docs/legal` 을 `res/raw` 로 복사해서 보여 주고
 * (app/build.gradle.kts 의 `copyLegalDocuments`), 사이트는 같은 원본을 `docs/site` 로
 * 복사해서 공개한다(루트 build.gradle.kts 의 `syncSiteDocuments`). 복사를 잊으면
 * **앱에 적힌 처리방침과 공개된 처리방침이 다른 상태**가 된다. 눈으로는 알 수 없고,
 * 알게 되는 시점은 보통 누군가 지적한 뒤다.
 *
 * `./gradlew testDebugUnitTest` 는 단계마다·CI 마다 돌아가므로, 거기에 끼워 두면
 * 어긋난 채로는 커밋이 지나가지 못한다.
 *
 * 저장소 경로는 테스트 태스크가 넘겨 주는 시스템 속성 `ansim.repoRoot` 로 받는다.
 * 작업 디렉터리에 기대지 않으려는 것이다(실행하는 쪽에 따라 달라진다).
 */
class SiteDocumentsTest {

    /** 사이트 페이지 한 장과 그 원본. 루트 build.gradle.kts 의 `siteDocuments` 와 짝이 맞아야 한다. */
    private data class SitePage(
        val source: String,
        val target: String,
        val title: String,
        val permalink: String,
    )

    private val pages = listOf(
        SitePage(
            source = "docs/legal/privacy-policy.md",
            target = "docs/site/privacy-policy.md",
            title = "개인정보 처리방침",
            permalink = "/privacy-policy/",
        ),
        SitePage(
            source = "docs/legal/location-terms.md",
            target = "docs/site/location-terms.md",
            title = "위치기반서비스 이용약관",
            permalink = "/location-terms/",
        ),
        SitePage(
            source = "docs/legal/terms-of-service.md",
            target = "docs/site/terms-of-service.md",
            title = "이용약관",
            permalink = "/terms-of-service/",
        ),
        SitePage(
            source = "docs/account-deletion.md",
            target = "docs/site/account-deletion.md",
            title = "계정 및 데이터 삭제 요청",
            permalink = "/account-deletion/",
        ),
    )

    private val repositoryRoot: File by lazy {
        val path = requireNotNull(System.getProperty("ansim.repoRoot")) {
            "시스템 속성 ansim.repoRoot 가 없다. app/build.gradle.kts 의 Test 설정을 확인할 것."
        }
        File(path)
    }

    @Test
    fun `사이트 페이지 본문은 원본과 글자까지 같다`() {
        pages.forEach { page ->
            val source = repositoryRoot.resolve(page.source)
            val target = repositoryRoot.resolve(page.target)
            assertTrue(page.source + " 가 없다", source.isFile)
            assertTrue(
                page.target + " 가 없다. `./gradlew syncSiteDocuments` 로 만든다.",
                target.isFile,
            )

            assertEquals(
                page.target + " 가 원본과 다르다. `./gradlew syncSiteDocuments` 를 돌릴 것.",
                normalize(source.readText()),
                normalize(bodyOf(target.readText(), page.target)),
            )
        }
    }

    @Test
    fun `사이트 페이지에는 Jekyll 앞머리가 붙어 있다`() {
        pages.forEach { page ->
            val frontMatter = frontMatterOf(repositoryRoot.resolve(page.target).readText(), page.target)
            assertTrue(
                page.target + " 의 title 이 '" + page.title + "' 가 아니다",
                frontMatter.contains("title: " + page.title),
            )
            assertTrue(
                page.target + " 의 permalink 가 '" + page.permalink + "' 가 아니다",
                frontMatter.contains("permalink: " + page.permalink),
            )
        }
    }

    @Test
    fun `공개할 약관이 늘어나면 사이트에도 같이 올라간다`() {
        // docs/legal 의 마크다운은 전부 앱 안에서 보여 준다(copyLegalDocuments 가 res/raw 로
        // 복사한다). 그러니 공개 사이트에도 전부 있어야 한다. 약관을 하나 더 만들고
        // 사이트에 올리는 것을 잊는 경우를 잡는다.
        val legalFiles = repositoryRoot.resolve("docs/legal")
            .listFiles { file -> file.isFile && file.name.endsWith(".md") }
            ?.map { "docs/legal/" + it.name }
            ?.sorted()
            .orEmpty()

        assertTrue("docs/legal 에 마크다운이 없다", legalFiles.isNotEmpty())
        assertEquals(
            "앱이 보여 주는 약관과 사이트에 올린 약관이 다르다. " +
                "루트 build.gradle.kts 의 siteDocuments 와 이 테스트의 pages 에 같이 추가할 것.",
            legalFiles,
            pages.map { it.source }.filter { it.startsWith("docs/legal/") }.sorted(),
        )
    }

    @Test
    fun `사이트 첫 화면이 모든 문서를 가리킨다`() {
        val index = repositoryRoot.resolve("docs/site/index.md")
        assertTrue("docs/site/index.md 가 없다", index.isFile)
        val text = index.readText()
        pages.forEach { page ->
            assertTrue(
                "docs/site/index.md 에 " + page.permalink + " 링크가 없다",
                text.contains("(" + page.permalink + ")"),
            )
        }
    }

    /** 앞머리(front matter)와 '만들어진 파일' 표시를 떼고 본문만 남긴다. */
    private fun bodyOf(text: String, name: String): String {
        val afterFrontMatter = text.substringAfter("---", missingDelimiterValue = "")
            .substringAfter("---", missingDelimiterValue = "")
        assertTrue(name + " 에 Jekyll 앞머리(--- 로 감싼 블록)가 없다", afterFrontMatter.isNotEmpty())
        val body = afterFrontMatter.substringAfter("-->", missingDelimiterValue = "")
        assertTrue(name + " 에 '만들어진 파일' 주석이 없다", body.isNotEmpty())
        return body
    }

    private fun frontMatterOf(text: String, name: String): String {
        assertTrue(name + " 가 '---' 로 시작하지 않는다", text.trimStart().startsWith("---"))
        return text.substringAfter("---").substringBefore("---")
    }

    // 줄바꿈과 앞뒤 빈 줄만 맞춘다.
    //
    // - 줄바꿈: .gitattributes 의 `text=auto` 때문에 작업 폴더의 파일이 플랫폼마다 다르다.
    // - 앞뒤 빈 줄: 사이트 페이지는 앞머리와 본문 사이에 빈 줄이 있다. 그 자리의 빈 줄을
    //   세는 것은 '같은 내용인가'와 아무 상관이 없다.
    //
    // 그 밖의 공백은 건드리지 않는다. 마크다운에서 줄 끝 공백 두 개는 줄바꿈이고,
    // 들여쓰기는 목록·인용의 구조라서 다르면 정말 다른 문서다.
    private fun normalize(text: String): String = text.replace("\r\n", "\n").trim()
}
