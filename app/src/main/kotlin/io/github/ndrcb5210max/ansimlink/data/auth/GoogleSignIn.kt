package io.github.ndrcb5210max.ansimlink.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.ndrcb5210max.ansimlink.BuildConfig
import io.github.ndrcb5210max.ansimlink.data.AnsimException
import io.github.ndrcb5210max.ansimlink.data.AppError

/**
 * Credential Manager + Sign in with Google 로 ID 토큰을 받아 온다.
 *
 * Web client ID(OAuth 2.0 '웹 애플리케이션' 클라이언트)는 프로젝트마다 다르므로
 * `local.properties` 의 `googleWebClientId` 또는 환경 변수 `GOOGLE_WEB_CLIENT_ID` 로 받는다
 * (app/build.gradle.kts 참고). 값이 없으면 [isAvailable] 가 false 가 되고 화면은 버튼을 숨긴다.
 */
class GoogleSignInClient(
    private val credentialManager: CredentialManager,
    private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID,
) {

    val isAvailable: Boolean get() = webClientId.isNotBlank()

    /**
     * Google 계정 선택 화면을 띄우고 ID 토큰을 돌려준다.
     *
     * @param activityContext 반드시 Activity 컨텍스트여야 한다(계정 선택 화면을 띄워야 하므로).
     */
    suspend fun requestIdToken(activityContext: Context): String {
        if (!isAvailable) throw AnsimException(AppError.GoogleSignInUnavailable)

        // '로그인' 버튼을 눌러 들어오는 흐름이므로 계정 선택기를 항상 보여 주는 쪽을 쓴다.
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val response = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: GetCredentialCancellationException) {
            throw AnsimException(AppError.GoogleSignInCancelled, e)
        } catch (e: NoCredentialException) {
            // 기기에 쓸 수 있는 Google 계정이 없을 때.
            throw AnsimException(AppError.GoogleSignInUnavailable, e)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager 요청 실패", e)
            throw AnsimException(AppError.GoogleSignInUnavailable, e)
        }

        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        Log.w(TAG, "예상하지 못한 자격 증명 종류: ${credential.type}")
        throw AnsimException(AppError.GoogleSignInUnavailable)
    }

    private companion object {
        const val TAG = "GoogleSignInClient"
    }
}
