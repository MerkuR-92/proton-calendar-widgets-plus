package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.FetchedEventsMetadataEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject


class UpdateFetchedEventsMetadataUseCase @Inject constructor(
    private val database: AppDatabase
) {

    suspend fun execute(
        userId: String,
        calendarIds: List<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ) {

        val windowStart = fromDate.atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond()
        val windowEnd = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond()

        database.inTransaction {
            calendarIds.forEach {

                val shouldAddNewEntry = database.fetchedEventsMetadataDao().hasWindowFullyOverlapping(
                    userId,
                    it,
                    windowStart,
                    windowEnd
                ).not() && database.calendarsDao().hasCalendar(it)

                if (shouldAddNewEntry) {
                    database.fetchedEventsMetadataDao().insert(
                        FetchedEventsMetadataEntity(
                            userId,
                            it,
                            windowStart,
                            windowEnd
                        )
                    )
                }
            }
        }
    }

    suspend fun isWindowFullyFetched(
        userId: String,
        calendarId: String,
        windowStart: Instant,
        windowEnd: Instant,
    ): Boolean {
        return database.inTransaction {
            database.fetchedEventsMetadataDao().hasWindowFullyOverlapping(
                userId = userId,
                calendarId = calendarId,
                windowStart = windowStart.epochSecond,
                windowEnd = windowEnd.epochSecond
            )
        }
    }

    suspend fun shouldFetch(
        userId: String,
        calendarId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Boolean {

        val windowStart = fromDate.atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond()
        val windowEnd = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond()

        return database.inTransaction {
            database.fetchedEventsMetadataDao().hasWindowFullyOverlapping(
                userId,
                calendarId,
                windowStart,
                windowEnd
            )
        }.not()
    }

}
