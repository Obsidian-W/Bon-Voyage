package paris.obsidian.bonvoyage

import android.content.Context
import androidx.room.*
import paris.obsidian.bonvoyage.days.*
import paris.obsidian.bonvoyage.trips.*

@Database(entities = [Trip::class, Day::class], version = 1, exportSchema = true)
abstract class DatabaseClient : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun dayDao(): DayDao
    companion object {
        @Volatile private var instance: DatabaseClient? = null
        fun getInstance(context: Context): DatabaseClient = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DatabaseClient::class.java, "planner.db")
                .build().also { instance = it }
        }
    }
}
