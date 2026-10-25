package io.github.ndrcb5210max.ansimlink.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import io.github.ndrcb5210max.ansimlink.domain.share.LocationPoint
import kotlinx.coroutines.tasks.await

/**
 * 내 마지막 위치를 한 번 읽는다.
 *
 * 쓰는 곳은 **가족 위치 보기 화면의 거리 계산 하나**다. '지은님이 1.2km 떨어져 있다'를
 * 보여 주려면 내 위치가 있어야 한다.
 *
 * ## 이 값은 어디로도 보내지 않는다
 * 공유 세션이 아니므로 Firestore 에 쓰지 않는다. 화면에서 거리를 셀 때만 쓰고 버린다.
 *
 * ## `getLastLocation` 을 쓰는 이유
 * 새 위치를 요청하지 않는다(`requestLocationUpdates`). 시스템이 다른 앱을 위해 이미
 * 받아 둔 위치를 읽기만 하므로 GPS 를 켜지 않고 배터리도 쓰지 않는다. 거리 표시는
 * 몇십 미터 틀려도 쓸모가 있다.
 */
object LastKnownLocation {

    private const val TAG = "LastKnownLocation"

    /**
     * @return 마지막 위치. 권한이 없거나 시스템이 들고 있는 위치가 없으면 null.
     *
     * 권한 검사를 이 함수 안에 그대로 적은 이유: lint 의 MissingPermission 검사는
     * 위치 API 를 부르는 **같은 함수 안에 있는** checkSelfPermission 만 알아본다
     * (3단계 '막힌 점' 6번과 같은 문제다).
     */
    suspend fun read(context: Context): LocationPoint? {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        return try {
            val location = LocationServices.getFusedLocationProviderClient(context)
                .lastLocation
                .await()
                ?: return null
            LocationPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                atMillis = System.currentTimeMillis(),
            )
        } catch (error: SecurityException) {
            // 권한이 방금 빠진 경우.
            Log.w(TAG, "마지막 위치를 읽지 못했다", error)
            null
        }
    }
}
