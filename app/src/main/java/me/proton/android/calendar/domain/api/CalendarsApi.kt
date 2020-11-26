package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.*
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.protonApi.GenericResponse
import retrofit2.Response
import retrofit2.http.Body

interface CalendarsApi {

    /**
     * Gets all user's calendars.
     */
    suspend fun getCalendars(userId: UserId): ApiResponse<CalendarsApiResponse>

    /**
     * Gets all events for given calendar, happening between timestamps in given timezone.
     */
    suspend fun getEvents(
        userId: UserId,
        calendarId: String,
        startTimestamp: Long,
        endTimestamp: Long,
        timezone: String,
        type: Int,
        page: Int,
        pageSize: Int
    ): ApiResponse<EventsApiResponse>

    /**
     * Get single event.
     */
    suspend fun getEvent(userId: UserId, calendarId: String, eventId: String) : ApiResponse<EventApiResponse>

    /**
     * Gets bootstrap for calendar setup.
     */
    suspend fun getBootstrap(userId: UserId, calendarId: String): ApiResponse<BootstrapApiResponse>

    /**
     * Gets all "active" (occuring in the future) alarms of type "DISPLAY" for given calendar.
     */
    suspend fun getAlarms(userId: UserId, calendarId: String, startTimestamp: Long, endTimestamp: Long, pageSize: Int) : ApiResponse<AlarmsApiResponse>

    /**
     * Delete an event.
     */
    suspend fun deleteEvent(userId: UserId, calendarId: String, eventId: String) : ApiResponse<StatusCodeApiResponse>

    suspend fun syncEvents(userId: UserId, calendarId: String, body: SyncEventsUpdateApiRequest) : ApiResponse<SyncEventsApiResponse>

    suspend fun getEventsByUid(userId: UserId, eventUid: String, page: Int, pageSize: Int) : ApiResponse<EventsByUidApiResponse>

    /**
     * Update calendar.
     */
    suspend fun updateCalendar(userId: UserId, calendarId: String, body: UpdateCalendarApiRequest): ApiResponse<UpdateCalendarApiResponse>

    suspend fun updateCalendarDisplay(userId: UserId, calendarId: String, body: UpdateCalendarDisplayApiRequest): ApiResponse<UpdateCalendarApiResponse>

}

