package io.github.ndrcb5210max.ansimlink.data.alert

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 기기 안에만 있는 로컬 DB. 지금은 발동 기록과(디버그용) 받은 알림 패키지 기록만 담는다.
 *
 * `exportSchema = false` 로 둔 이유: 아직 버전 1이고 마이그레이션이 없다. 스키마를 바꿀 때는
 * 버전을 올리고 스키마 내보내기와 마이그레이션을 같이 넣는다.
 */
@Database(
    entities = [AlertEventEntity::class, ObservedPackageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AnsimDatabase : RoomDatabase() {

    abstract fun alertEvents(): AlertEventDao

    abstract fun observedPackages(): ObservedPackageDao

    companion object {
        private const val NAME = "ansim-link.db"

        fun create(context: Context): AnsimDatabase =
            Room.databaseBuilder(context, AnsimDatabase::class.java, NAME).build()
    }
}
