package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.*

interface CalendarsApi {

    /**
     * Gets all user's calendars.
     */
    suspend fun getCalendars(): ApiResponse<CalendarsApiResponse>

    /**
     * Gets all events for given calendar, happening between timestamps in given timezone.
     */
    suspend fun getEvents(
        /*userId: String,*/
        calendarId: String,
        startTimestamp: Long,
        endTimestamp: Long,
        timezone: String
    ): ApiResponse<EventsApiResponse>

    /**
     * Gets bootstrap for calendar setup.
     */
    suspend fun getBootstrap(calendarId: String): ApiResponse<BootstrapApiResponse>

    /**
     * Gets all "active" (occuring in the future) alarms of type "DISPLAY" for given calendar.
     */
    suspend fun getAlarms(calendarId: String) : ApiResponse<AlarmsApiResponse>

    /**
     * Delete an event.
     */
    suspend fun deleteEvent(calendarId: String, eventId: String) : ApiResponse<StatusCodeApiResponse>

    suspend fun syncEvents(calendarId: String, body: SyncEventsUpdateApiRequest) : ApiResponse<SyncEventsApiResponse>

}

