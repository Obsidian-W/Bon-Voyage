package paris.obsidian.bonvoyage.days

import androidx.room.*

@Dao
interface DayDao {
    @Query("SELECT * FROM Day WHERE tripID = :tripID ORDER BY id")
    fun observe(tripID: Int): kotlinx.coroutines.flow.Flow<List<Day>>

    @Query("SELECT * FROM Day WHERE id = :id")
    fun findByLongID(id: Long): Day?

    @Query("DELETE FROM Day WHERE tripID = :tripID")
    fun removeForTrip(tripID: Int)
    @Query("SELECT * FROM day WHERE tripID = :tripID ORDER BY id")
    fun getAll(tripID: Int): List<Day>

    @Query("SELECT * FROM day WHERE id LIKE :id LIMIT 1")
    fun findByID(id: Int): Day

    @Insert
    fun insertAll(vararg days: Day)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOne(day: Day): Long

    @Update
    fun updateOne(day: Day)

    @Delete
    fun removeOne(day: Day)
}
