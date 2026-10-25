package io.github.ndrcb5210max.ansimlink.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.github.ndrcb5210max.ansimlink.data.AnsimException
import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Users
import io.github.ndrcb5210max.ansimlink.data.model.SignedInUser
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsentState
import io.github.ndrcb5210max.ansimlink.domain.consent.toRecords
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Firebase Authentication + Firestore `users/{uid}` 를 쓰는 구현. */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val now: () -> Long = System::currentTimeMillis,
) : AuthRepository {

    override val currentUser: Flow<SignedInUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toSignedInUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override fun currentUserNow(): SignedInUser? = auth.currentUser?.toSignedInUser()

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        consents: SignUpConsentState,
    ): SignedInUser {
        require(consents.canSubmit) { "필수 동의가 모두 체크된 뒤에만 가입할 수 있다." }

        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: throw AnsimException(AppError.Unknown)

        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build(),
        ).await()

        writeUserDocument(user.uid, email.trim(), displayName.trim(), consents)

        // 인증 메일. 실패해도 가입 자체는 끝난 것이므로 막지 않는다(설정에서 다시 보낼 수 있다).
        runCatching { user.sendEmailVerification().await() }

        return user.toSignedInUser().copy(displayName = displayName.trim())
    }

    override suspend fun signInWithEmail(email: String, password: String): SignedInUser {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return result.user?.toSignedInUser() ?: throw AnsimException(AppError.InvalidCredentials)
    }

    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override suspend fun resendVerificationEmail() {
        val user = auth.currentUser ?: throw AnsimException(AppError.InvalidCredentials)
        user.sendEmailVerification().await()
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        consents: SignUpConsentState,
    ): SignedInUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: throw AnsimException(AppError.Unknown)

        // 처음 들어온 사람일 때만 동의 기록을 남긴다. 기존 사용자의 기록을 덮어쓰면 안 된다.
        if (result.additionalUserInfo?.isNewUser == true) {
            if (!consents.canSubmit) {
                // 화면이 막고 있어서 여기까지 오면 안 되지만, 왔다면 동의 없이 계정만 만들어진 상태다.
                // 그대로 두면 동의 기록 없는 계정이 남으므로 방금 만든 계정을 지운다.
                runCatching { user.delete().await() }
                throw AnsimException(AppError.Unknown)
            }
            writeUserDocument(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = user.displayName.orEmpty(),
                consents = consents,
            )
        }
        return user.toSignedInUser()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: throw AnsimException(AppError.InvalidCredentials)
        val email = user.email ?: throw AnsimException(AppError.InvalidCredentials)
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
    }

    override suspend fun reauthenticateWithGoogle(idToken: String) {
        val user = auth.currentUser ?: throw AnsimException(AppError.InvalidCredentials)
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun deleteAuthUser() {
        val user = auth.currentUser ?: throw AnsimException(AppError.InvalidCredentials)
        user.delete().await()
    }

    /**
     * `users/{uid}` 에 프로필과 동의 기록을 쓴다.
     *
     * 동의 항목·시각·약관 버전을 같이 남긴다(위치정보법 제18조·제19조의 '확인자료').
     */
    private suspend fun writeUserDocument(
        uid: String,
        email: String,
        displayName: String,
        consents: SignUpConsentState,
    ) {
        val consentMap = consents.toRecords(now()).mapValues { (_, record) ->
            mapOf(
                Users.CONSENT_ACCEPTED to record.accepted,
                Users.CONSENT_VERSION to record.version,
                Users.CONSENT_AT to record.acceptedAtMillis,
            )
        }
        val document = mapOf(
            Users.EMAIL to email,
            Users.DISPLAY_NAME to displayName,
            Users.CONSENTS to consentMap,
            Users.CREATED_AT to FieldValue.serverTimestamp(),
        )
        firestore.collection(Users.COLLECTION).document(uid)
            .set(document, SetOptions.merge())
            .await()
    }
}

private fun FirebaseUser.toSignedInUser(): SignedInUser = SignedInUser(
    uid = uid,
    email = email,
    displayName = displayName,
    emailVerified = isEmailVerified,
    signedInWithGoogle = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID },
)
