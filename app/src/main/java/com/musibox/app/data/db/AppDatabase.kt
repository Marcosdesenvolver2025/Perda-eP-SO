package com.musibox.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        Song::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlayHistoryEntity::class,
        PlayStatEntity::class,
        QueueItemEntity::class,
        DownloadEntity::class,
        VaultItemEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao
    abstract fun downloadDao(): DownloadDao
    abstract fun vaultDao(): VaultDao

    companion object {
        const val VERSION = 2

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "musibox.db")
                .addMigrations(*Migrations.ALL)
                // Sem fallbackToDestructiveMigration: atualizações nunca apagam dados do usuário.
                .build()
    }
}

/**
 * Migrations do banco. A cada nova versão do esquema, adicione aqui uma Migration(n, n+1)
 * e aumente AppDatabase.VERSION. O esquema de cada versão fica exportado em app/schemas.
 */
object Migrations {
    /** 1 → 2: Music Edits salvos nas músicas das playlists. */
    private val M1_2 = object : Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN fx TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(M1_2)
}
