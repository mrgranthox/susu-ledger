package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.util.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
  entities = [
    IdentityEntity::class,
    UserEntity::class,
    GroupEntity::class,
    MemberEntity::class,
    CycleEntity::class,
    AccountEntity::class,
    PaymentEntity::class,
    LedgerEntryEntity::class,
    ClaimEntity::class,
    LedgerCorrectionEntity::class,
    AuditLogEntity::class,
    MessageLogEntity::class
  ],
  version = 3,
  exportSchema = false
)
abstract class SusuDatabase : RoomDatabase() {

  abstract fun susuDao(): SusuDao

  companion object {
    @Volatile
    private var INSTANCE: SusuDatabase? = null

    fun getDatabase(context: Context, scope: CoroutineScope): SusuDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          SusuDatabase::class.java,
          "susu_ledger.db"
        )
          .addCallback(SusuDatabaseCallback(scope))
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }

    private class SusuDatabaseCallback(
      private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Clean slate initialization: no hardcoded or mock data inserted.
        // User creates group and members organically during initial onboarding.
      }
    }
  }
}
