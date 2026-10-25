package io.github.ndrcb5210max.ansimlink

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import io.github.ndrcb5210max.ansimlink.data.ServerConfig
import io.github.ndrcb5210max.ansimlink.data.ServerStatus
import io.github.ndrcb5210max.ansimlink.data.account.AccountDeleter
import io.github.ndrcb5210max.ansimlink.data.alert.AlertDetector
import io.github.ndrcb5210max.ansimlink.data.alert.AlertEventRepository
import io.github.ndrcb5210max.ansimlink.data.alert.AnsimDatabase
import io.github.ndrcb5210max.ansimlink.data.alert.TriggerRulesStore
import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.data.auth.FirebaseAuthRepository
import io.github.ndrcb5210max.ansimlink.data.auth.GoogleSignInClient
import io.github.ndrcb5210max.ansimlink.data.billing.BillingRepository
import io.github.ndrcb5210max.ansimlink.data.billing.EntitlementRepository
import io.github.ndrcb5210max.ansimlink.data.billing.FirestoreEntitlementRepository
import io.github.ndrcb5210max.ansimlink.data.billing.FunctionsSubscriptionVerifier
import io.github.ndrcb5210max.ansimlink.data.billing.PlanProvider
import io.github.ndrcb5210max.ansimlink.data.billing.SubscriptionVerifier
import io.github.ndrcb5210max.ansimlink.data.billing.createBillingRepository
import io.github.ndrcb5210max.ansimlink.data.group.FirestoreGroupRepository
import io.github.ndrcb5210max.ansimlink.data.group.GroupRepository
import io.github.ndrcb5210max.ansimlink.data.message.SafetyMessageStore
import io.github.ndrcb5210max.ansimlink.data.share.FirestoreShareRepository
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionRecordRepository
import io.github.ndrcb5210max.ansimlink.data.share.ShareRepository
import io.github.ndrcb5210max.ansimlink.data.share.ShareSettingsStore

/**
 * 수동 주입 컨테이너(CLAUDE.md: DI 는 단순하게 AppContainer 로).
 *
 * Firebase 객체는 설정이 없으면 만들 수 없으므로 전부 `by lazy` 로 둔다.
 * [serverStatus] 가 [ServerStatus.NotConfigured] 면 화면이 안내만 보여 주고
 * 아래 저장소들을 아예 건드리지 않는다.
 */
class AppContainer(private val applicationContext: Context) {

    val serverStatus: ServerStatus by lazy { ServerConfig.status(applicationContext) }

    val isServerReady: Boolean get() = serverStatus == ServerStatus.Ready

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(firebaseAuth, firestore)
    }

    val groupRepository: GroupRepository by lazy {
        FirestoreGroupRepository(firestore)
    }

    val googleSignInClient: GoogleSignInClient by lazy {
        GoogleSignInClient(CredentialManager.create(applicationContext))
    }

    val accountDeleter: AccountDeleter by lazy {
        AccountDeleter(firestore, authRepository, groupRepository)
    }

    // --- 3단계: 재난문자 감지 ---
    //
    // 아래는 Firebase 와 상관이 없다. 감지·발동 기록·발동 규칙은 모두 기기 안에서만 돌고
    // 서버로 아무것도 보내지 않으므로, '서버 설정이 필요합니다' 상태에서도 그대로 동작한다.

    private val database: AnsimDatabase by lazy { AnsimDatabase.create(applicationContext) }

    val alertEventRepository: AlertEventRepository by lazy {
        AlertEventRepository(database.alertEvents(), database.observedPackages())
    }

    val triggerRulesStore: TriggerRulesStore by lazy { TriggerRulesStore(applicationContext) }

    val alertDetector: AlertDetector by lazy {
        AlertDetector(alertEventRepository, triggerRulesStore)
    }

    // --- 4단계: 위치 공유와 안심 메시지 ---
    //
    // 설정(공유 시간·자동 시작)과 안심 메시지 원고는 기기 안에만 있다. 제공 기록도 기기에
    // 사본을 둔다(서버 사본은 Firestore 에 따로 남는다). 그래서 '서버 설정이 필요합니다'
    // 상태에서도 이 셋은 그대로 동작한다. 실제 위치 전송만 Firebase 가 필요하다.

    val shareSettingsStore: ShareSettingsStore by lazy { ShareSettingsStore(applicationContext) }

    val safetyMessageStore: SafetyMessageStore by lazy { SafetyMessageStore(applicationContext) }

    val provisionRecordRepository: ProvisionRecordRepository by lazy {
        ProvisionRecordRepository(database.provisionRecords())
    }

    /** 위치 전송만 Firebase 가 필요하다. 설정이 없으면 건드리지 않는다. */
    val shareRepository: ShareRepository by lazy {
        FirestoreShareRepository(firestore)
    }

    // --- 5단계: 구독 결제 ---
    //
    // 구독 상태는 서버가 `users/{uid}.entitlement` 에 쓰고 앱은 읽기만 한다. 보안 규칙이
    // 클라이언트 쓰기를 막는다. 서버 설정이 없으면(가짜 google-services.json) 늘 무료다.

    val entitlementRepository: EntitlementRepository by lazy {
        FirestoreEntitlementRepository(firestore)
    }

    /**
     * 지금 요금제. **요금제를 보는 모든 곳이 이것 하나를 읽는다.**
     *
     * 그룹 한도·공유 시간 상한·유형별 메시지·결제 화면·설정 > 구독 다섯 곳이 각자
     * 판정하면 서로 어긋난다([PlanProvider] 주석 참고).
     */
    val planProvider: PlanProvider by lazy {
        PlanProvider(
            authRepository = authRepository,
            entitlements = entitlementRepository,
            serverReady = isServerReady,
            scope = AnsimLinkApplication.applicationScope,
        )
    }

    /** 구매 토큰 검증 요청. 실제 검증은 서버(callable 함수)가 한다. */
    val subscriptionVerifier: SubscriptionVerifier by lazy {
        FunctionsSubscriptionVerifier(FirebaseFunctions.getInstance(FUNCTIONS_REGION))
    }

    /**
     * Play 결제. 디버그 빌드에서는 가짜 저장소로 바꿀 수 있다
     * (`app/src/debug/.../BillingRepositoryFactory.kt`).
     */
    val billingRepository: BillingRepository by lazy {
        createBillingRepository(applicationContext)
    }

    private companion object {
        /** functions/src/index.ts 의 REGION 과 같아야 한다. 다르면 함수를 못 찾는다. */
        const val FUNCTIONS_REGION = "asia-northeast3"
    }
}
