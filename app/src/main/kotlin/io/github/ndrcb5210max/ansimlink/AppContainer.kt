package io.github.ndrcb5210max.ansimlink

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import io.github.ndrcb5210max.ansimlink.data.group.FirestoreGroupRepository
import io.github.ndrcb5210max.ansimlink.data.group.GroupRepository

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
}
