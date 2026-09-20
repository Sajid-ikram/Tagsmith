package com.tagsmith.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun toStatus(value: String): TagStatus = TagStatus.valueOf(value)
    @TypeConverter fun fromStatus(value: TagStatus): String = value.name
    @TypeConverter fun toAction(value: String): HistoryAction = HistoryAction.valueOf(value)
    @TypeConverter fun fromAction(value: HistoryAction): String = value.name
}

@Database(
    entities = [TagRecord::class, HistoryEntry::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TagsmithDatabase : RoomDatabase() {
    abstract fun tags(): TagDao
    abstract fun history(): HistoryDao

    companion object {
        fun build(context: Context): TagsmithDatabase =
            Room.databaseBuilder(context, TagsmithDatabase::class.java, "tagsmith.db")
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
