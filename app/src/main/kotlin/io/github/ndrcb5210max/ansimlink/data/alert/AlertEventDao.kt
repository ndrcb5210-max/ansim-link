package io.github.ndrcb5210max.ansimlink.data.alert

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {

    @Insert
    suspend fun insert(event: AlertEventEntity): Long

    /** 발동 기록 화면이 보는 목록. 최근 것부터. */
    @Query("SELECT * FROM alert_events ORDER BY detectedAtMillis DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<AlertEventEntity>>

    /** 중복 판단에 쓰는, 최근에 **발동한** 기록. */
    @Query(
        "SELECT * FROM alert_events " +
            "WHERE triggered = 1 AND detectedAtMillis >= :sinceMillis " +
            "ORDER BY detectedAtMillis DESC",
    )
    suspend fun triggeredSince(sinceMillis: Long): List<AlertEventEntity>

    @Query("DELETE FROM alert_events WHERE detectedAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("DELETE FROM alert_events")
    suspend fun deleteAll()
}

@Dao
interface ObservedPackageDao {

    @Query("SELECT * FROM observed_packages ORDER BY lastSeenAtMillis DESC")
    fun all(): Flow<List<ObservedPackageEntity>>

    @Query("SELECT * FROM observed_packages WHERE packageName = :packageName")
    suspend fun find(packageName: String): ObservedPackageEntity?

    @Upsert
    suspend fun upsert(entity: ObservedPackageEntity)

    @Query("DELETE FROM observed_packages")
    suspend fun deleteAll()

    /** 패키지 이름 하나를 '한 번 더 봤다'고 적는다. */
    @Transaction
    suspend fun record(packageName: String, nowMillis: Long) {
        val existing = find(packageName)
        upsert(
            existing?.copy(
                count = existing.count + 1,
                lastSeenAtMillis = nowMillis,
            ) ?: ObservedPackageEntity(
                packageName = packageName,
                count = 1,
                firstSeenAtMillis = nowMillis,
                lastSeenAtMillis = nowMillis,
            ),
        )
    }
}
