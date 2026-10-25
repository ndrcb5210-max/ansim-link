package io.github.ndrcb5210max.ansimlink.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * 사용자에게 보여 줄 수 있는 오류 종류.
 *
 * Firebase 예외 메시지는 영어이고 내부 사정이 섞여 있어서 그대로 보여 주지 않는다.
 * 여기서 종류만 뽑고, 문구는 화면에서 `strings.xml` 로 붙인다.
 */
enum class AppError {
    /** google-services.json 이 없거나 가짜다. */
    ServerNotConfigured,
    Network,
    EmailAlreadyInUse,
    InvalidCredentials,
    WeakPassword,

    /** 민감한 작업(계정 삭제) 전에 다시 로그인해야 한다. */
    RecentLoginRequired,
    PermissionDenied,
    InviteNotFound,
    InviteExpired,
    AlreadyMember,
    GroupFull,
    GroupLimitReached,
    GoogleSignInUnavailable,
    GoogleSignInCancelled,
    Unknown,
}

/** 앱 안에서 주고받는 오류. [error] 로 화면 문구를 정한다. */
class AnsimException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/** Firebase 예외를 [AppError] 로 바꾼다. 모르는 예외는 [AppError.Unknown]. */
fun Throwable.toAppError(): AppError = when (this) {
    is AnsimException -> error
    is FirebaseNetworkException -> AppError.Network
    is FirebaseAuthWeakPasswordException -> AppError.WeakPassword
    is FirebaseAuthUserCollisionException -> AppError.EmailAlreadyInUse
    is FirebaseAuthRecentLoginRequiredException -> AppError.RecentLoginRequired
    is FirebaseAuthInvalidUserException -> AppError.InvalidCredentials
    is FirebaseAuthInvalidCredentialsException -> AppError.InvalidCredentials
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionDenied
        FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.Network
        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.InviteNotFound
        else -> AppError.Unknown
    }
    else -> AppError.Unknown
}
