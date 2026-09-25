package com.tagsmith.core.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.tagsmith.core.nfc.PayloadType

class Converters {
    @TypeConverter fun toStatus(value: String): TagStatus = TagStatus.valueOf(value)
    @TypeConverter fun fromStatus(value: TagStatus): String = value.name
    @TypeConverter fun toAction(value: String): HistoryAction = HistoryAction.valueOf(value)
    @TypeConverter fun fromAction(value: HistoryAction): String = value.name
    @TypeConverter fun toPayloadType(value: String): PayloadType = PayloadType.valueOf(value)
    @TypeConverter fun fromPayloadType(value: PayloadType): String = value.name
    @TypeConverter fun toBatchStatus(value: String): BatchStatus = BatchStatus.valueOf(value)
    @TypeConverter fun fromBatchStatus(value: BatchStatus): String = value.name
}

@Database(
    entities = [TagRecord::class, HistoryEntry::class, Client::class, Template::class, Batch::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = TagsmithDatabase.V1ToV2::class)],
)
@TypeConverters(Converters::class)
abstract class TagsmithDatabase : RoomDatabase() {
    abstract fun tags(): TagDao
    abstract fun history(): HistoryDao
    abstract fun clients(): ClientDao
    abstract fun templates(): TemplateDao
    abstract fun batches(): BatchDao

    /**
     * v1 → v2 adds clients, templates and batches, and links tags and history
     * to them by id. v1 stored a free-text client name on tags that nothing could
     * set, so it is dropped rather than migrated — it was always null.
     */
    @DeleteColumn(tableName = "tags", columnName = "clientName")
    class V1ToV2 : AutoMigrationSpec

    companion object {
        fun build(context: Context): TagsmithDatabase =
            Room.databaseBuilder(context, TagsmithDatabase::class.java, "tagsmith.db")
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
