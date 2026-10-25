package io.github.ndrcb5210max.ansimlink.data.share

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.TypeConverter
import kotlinx.coroutines.flow.Flow

@Dao
interface ProvisionRecordDao {

    @Insert
    suspend fun insert(record: ProvisionRecordEntity): Long

    /** '내 위치 제공 기록' 화면이 보는 목록. 최근 것부터. */
    @Query(
        "SELECT * FROM provision_records " +
            "ORDER BY startedAtMillis DESC, id DESC LIMIT :limit",
    )
    fun recent(limit: Int): Flow<List<ProvisionRecordEntity>>

    /** 지금 진행 중인 기록. 서비스가 끝날 때 채우려고 찾는다. */
    @Query("SELECT * FROM provision_records WHERE sessionId = :sessionId")
    suspend fun bySession(sessionId: String): List<ProvisionRecordEntity>

    @Query(
        "UPDATE provision_records SET endedAtMillis = :endedAtMillis, writeCount = :writeCount " +
            "WHERE sessionId = :sessionId AND groupId = :groupId",
    )
    suspend fun markEnded(
        sessionId: String,
        groupId: String,
        endedAtMillis: Long,
        writeCount: Int,
    )

    /**
     * 끝나지 않은 기록을 모두 닫는다.
     *
     * 서비스가 비정상 종료되면(강제 종료, 시스템이 프로세스를 거둬감) `markEnded` 를
     * 부르지 못해 '아직 공유 중'으로 남는다. 앱을 다시 열 때 이 함수로 정리한다.
     * 그러지 않으면 제공 기록 화면에 끝나지 않은 줄이 영원히 남는다.
     */
    @Query(
        "UPDATE provision_records SET endedAtMillis = plannedEndAtMillis " +
            "WHERE endedAtMillis IS NULL AND plannedEndAtMillis < :nowMillis",
    )
    suspend fun closeStaleRecords(nowMillis: Long)

    @Query("DELETE FROM provision_records WHERE startedAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}

/**
 * `List<String>` 을 한 칸에 담는 변환기(제공받은 사람 이름).
 *
 * 줄바꿈으로 잇는다. 사용자 이름에는 줄바꿈을 넣을 수 없다(가입 화면이 한 줄 입력이다).
 * 쉼표로 이으면 '홍길동, 김철수' 같은 이름이 들어올 때 갈라진다.
 *
 * 테이블 하나를 더 만들지 않은 이유: 이 값은 읽을 때 통째로 쓰고 따로 조회하지 않는다.
 */
class StringListConverter {

    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\n"
    }
}
