package me.proton.android.calendar.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.proton.android.calendar.data.db.AppDatabase.Companion.TABLE_CALENDARS
import me.proton.android.calendar.data.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CalendarsDao : BaseDao<CalendarEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId")
    abstract fun flowCalendars(userId: String): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE flags == 1 AND fkUserId = :userId")
    abstract fun flowActiveCalendars(userId: String): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE flags & (32 + 64) >= 32 AND fkUserId = :userId")
    abstract fun flowDisabledCalendars(userId: String): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE flags & (2 + 4 + 8 + 16) >= 2 AND fkUserId = :userId")
    abstract fun flowInactiveCalendars(userId: String): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars")
    abstract fun flowCalendars(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars")
    abstract fun selectCalendars(): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId")
    abstract suspend fun selectCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT fkUserId FROM calendars WHERE id = :calendarId")
    abstract suspend fun selectCalendarUserId(calendarId: String): String?

    @Query("SELECT * FROM calendars WHERE id = :id")
    abstract suspend fun selectById(id: String): CalendarEntity?

    @Query("SELECT EXISTS(SELECT * FROM calendars WHERE id = :calendarId)")
    abstract suspend fun hasCalendar(calendarId: String): Boolean

    @Query("DELETE FROM calendars WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId AND calendars.display = 1")
    abstract suspend fun selectDisplayedCalendars(userId: String): List<CalendarEntity>

    @Query("UPDATE calendars SET flags = :flags WHERE id = :calendarId")
    abstract suspend fun updateCalendarFlags(calendarId: String, flags: Int)

    @Query("UPDATE calendars SET display = :display WHERE id = :calendarId")
    abstract suspend fun updateCalendarDisplay(calendarId: String, display: Int)

// @Query("SELECT * from plants WHERE growZoneNumber = :growZoneNumber ORDER BY name")
//fun getPlantsWithGrowZoneNumberFlow(growZoneNumber: Int): Flow<List<Plant>>


//    @Query("SELECT * FROM ${TABLE_CALENDARS}")
//    suspend fun selectCalendars(): List<CalendarApiEntity>

//    @Query("SELECT * FROM ${TABLE_CALENDARS}")
//    fun selectCalendarsLiveData(): LiveData<List<CalendarEntity>>
//
//
//
//    @Query("SELECT * FROM ${TABLE_CALENDARS}")
//    fun selectCalendarsFlowSingleItem(): Flow<CalendarEntity>
//



    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // Query type: Observable read: Flow<T> or LiveData<T>
    // one-shot read and write: suspend fun




//
    //@Query("SELECT * FROM user WHERE age > :minAge")
    //    fun loadAllUsersOlderThan(minAge: Int): Array<User>

    // @Query("SELECT * FROM user WHERE age BETWEEN :minAge AND :maxAge")
    //    fun loadAllUsersBetweenAges(minAge: Int, maxAge: Int): Array<User>

    //@Query("SELECT first_name, last_name FROM user WHERE region IN (:regions)")
    //    fun loadUsersFromRegions(regions: List<String>): List<NameTuple>

//    @Query("UPDATE users SET age = age + 1 WHERE userId = :userId")
//    suspend fun incrementUserAge(userId: String)
//
//    @Insert
//    suspend fun insertUser(user: User)
//
//    @Update
//    suspend fun updateUser(user: User) or vararg or list
//
//    @Delete
//    suspend fun deleteUser(user: User)

    // transaction methods can be suspending as well
//    @Transaction
//    open suspend fun setLoggedInUser(loggedInUser: User) {
//        deleteUser(loggedInUser)
//        insertUser(loggedInUser)
//    }
//
//    @Query("DELETE FROM users")
//    abstract suspend fun deleteUser(user: User)
//
//    @Insert
//    abstract suspend fun insertUser(user: User)


    // calling different suspend functions:
    /**

    class Repository(val database: MyDatabase) {

    suspend fun clearData(){
    database.withTransaction {
    database.userDao().deleteLoggedInUser() // suspend function
    database.commentsDao().deleteComments() // suspend function
    }
    }
    }

     */
}
