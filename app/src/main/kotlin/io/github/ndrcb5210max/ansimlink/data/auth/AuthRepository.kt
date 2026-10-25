package io.github.ndrcb5210max.ansimlink.data.auth

import io.github.ndrcb5210max.ansimlink.data.model.SignedInUser
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsentState
import kotlinx.coroutines.flow.Flow

/**
 * 로그인·가입·계정 삭제.
 *
 * 화면(ViewModel)은 이 인터페이스만 본다. Firebase 를 쓰지 않는 가짜 구현으로
 * 미리보기와 테스트를 할 수 있게 하려는 것이다.
 */
interface AuthRepository {

    /** 지금 로그인한 사람. 로그인하지 않았으면 null 을 흘린다. */
    val currentUser: Flow<SignedInUser?>

    /** 화면을 처음 그릴 때 쓰는 동기 값. */
    fun currentUserNow(): SignedInUser?

    /**
     * 이메일·비밀번호로 가입한다. 성공하면 인증 메일을 보내고
     * `users/{uid}` 에 동의 기록([consents])을 남긴다.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        consents: SignUpConsentState,
    ): SignedInUser

    suspend fun signInWithEmail(email: String, password: String): SignedInUser

    /** 비밀번호 재설정 메일을 보낸다. */
    suspend fun sendPasswordReset(email: String)

    /** 인증 메일을 다시 보낸다. */
    suspend fun resendVerificationEmail()

    /**
     * Google ID 토큰으로 로그인한다.
     *
     * 처음 들어온 사람이면 [consents] 를 `users/{uid}` 에 남긴다.
     * 이미 가입한 사람이면 동의 기록을 건드리지 않는다.
     */
    suspend fun signInWithGoogle(idToken: String, consents: SignUpConsentState): SignedInUser

    suspend fun signOut()

    /** 계정 삭제 전 재인증(이메일·비밀번호 계정). */
    suspend fun reauthenticateWithPassword(password: String)

    /** 계정 삭제 전 재인증(Google 계정). */
    suspend fun reauthenticateWithGoogle(idToken: String)

    /** Firebase Auth 계정 자체를 지운다. Firestore 정리가 끝난 뒤에 부른다. */
    suspend fun deleteAuthUser()
}
