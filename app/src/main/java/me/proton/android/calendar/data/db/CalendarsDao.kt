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

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId")
    abstract fun selectCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT fkUserId FROM calendars WHERE id = :calendarId")
    abstract fun selectCalendarUserId(calendarId: String): String?

    @Query("SELECT * FROM calendars WHERE id = :id")
    abstract fun selectById(id: String): CalendarEntity?

    @Query("DELETE FROM calendars WHERE id = :id")
    abstract fun deleteById(id: String)

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId AND calendars.display = 1")
    abstract fun selectDisplayedCalendars(userId: String): List<CalendarEntity>



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
//    abstract fun deleteUser(user: User)
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
