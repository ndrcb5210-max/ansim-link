package io.github.ndrcb5210max.ansimlink.data.alert

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionRecordDao
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionRecordEntity
import io.github.ndrcb5210max.ansimlink.data.share.StringListConverter

/**
 * 기기 안에만 있는 로컬 DB.
 *
 * 담는 것: 발동 기록(3단계), 받은 알림 패키지 기록(디버그 전용, 3단계),
 * 내 위치 제공 기록(4단계).
 *
 * 버전 2 로 올렸다. 3단계에서는 `exportSchema = false` 로 두고 마이그레이션 없이 갔는데,
 * 지금은 **이미 설치된 앱에 발동 기록이 쌓여 있다.** `fallbackToDestructiveMigration` 으로
 * 밀어 버리면 사용자가 보던 발동 기록이 사라진다. 그래서 테이블을 더하는
 * 마이그레이션 하나를 직접 적었다.
 *
 * `exportSchema` 는 여전히 false 다. 스키마 JSON 을 저장소에 넣으면 그것으로
 * 마이그레이션 테스트를 쓸 수 있는데, 그 테스트는 계측 테스트(기기 필요)라서
 * 이 환경에서 돌릴 수 없다. 내보내 두고 검증하지 않는 쪽보다 남은 일로 적어 두는 쪽을 골랐다.
 */
@Database(
    entities = [
        AlertEventEntity::class,
        ObservedPackageEntity::class,
        ProvisionRecordEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(StringListConverter::class)
abstract class AnsimDatabase : RoomDatabase() {

    abstract fun alertEvents(): AlertEventDao

    abstract fun observedPackages(): ObservedPackageDao

    abstract fun provisionRecords(): ProvisionRecordDao

    companion object {
        private const val NAME = "ansim-link.db"

        /**
         * 1 → 2: 위치 제공 기록 테이블을 더한다. 기존 테이블은 건드리지 않는다.
         *
         * 컬럼 정의는 [ProvisionRecordEntity] 와 글자까지 맞아야 한다. 어긋나면 Room 이
         * 앱을 켤 때 `IllegalStateException: Migration didn't properly handle` 로 죽는다.
         * `receiverNames` 는 [StringListConverter] 가 TEXT 한 칸으로 바꿔 넣는다.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `provision_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, " +
                        "`groupId` TEXT NOT NULL, " +
                        "`groupName` TEXT NOT NULL, " +
                        "`startedAtMillis` INTEGER NOT NULL, " +
                        "`plannedEndAtMillis` INTEGER NOT NULL, " +
                        "`endedAtMillis` INTEGER, " +
                        "`purposeKey` TEXT NOT NULL, " +
                        "`receiverNames` TEXT NOT NULL, " +
                        "`writeCount` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_provision_records_startedAtMillis` " +
                        "ON `provision_records` (`startedAtMillis`)",
                )
            }
        }

        fun create(context: Context): AnsimDatabase =
            Room.databaseBuilder(context, AnsimDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
