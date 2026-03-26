package me.proton.android.calendar.test.domain.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import me.proton.android.calendar.R
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatEnd
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.MeetIntegrationType
import me.proton.android.calendar.domain.usecase.EditCreateEventUseCase
import me.proton.android.calendar.domain.usecase.SendEmailDirect
import me.proton.android.calendar.domain.usecase.SendEmailUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpgradeEventUseCase
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.user.domain.UserAddressManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SendEmailUseCaseTest {

    private val logger = TestsLogger
    private val sendEmailDirectMockUseCase: SendEmailDirect = mockk()
    private val userAddressManagerMock: UserAddressManager = mockk()
    private val json = Json { this.ignoreUnknownKeys = true }
    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val calendarsRepositoryMock: CalendarsRepository = mockk()
    private val valuesStoreProviderMock: ValueStoreProvider = mockk()
    private val cryptoMock: Crypto = mockk()
    private val editCreateEventUseCaseMock: EditCreateEventUseCase = mockk()
    private val resourceProviderMock: ResourceProvider = mockk()
    private val cryptoContextMock: CryptoContext = mockk()
    private val upgradeEventUseCaseMock: UpgradeEventUseCase = mockk()

    private val sendEmailUseCase = SendEmailUseCase(
        logger,
        sendEmailDirectUseCase = sendEmailDirectMockUseCase,
        userAddressManager = userAddressManagerMock,
        json = json,
        transformEventUseCase = transformEventUseCaseMock,
        calendarsRepository = calendarsRepositoryMock,
        valueStoreProvider = valuesStoreProviderMock,
        crypto = cryptoMock,
        editCreateEventUseCase = editCreateEventUseCaseMock,
        resourceProvider = resourceProviderMock,
        cryptoContext = cryptoContextMock,
        upgradeEventUseCase = upgradeEventUseCaseMock
    )

    @Before
    fun beforeEach() {
        clearAllMocks()
    }

    @Test
    fun test_getInviteMailBody_with_all_day_event_that_spans_single_day_and_actual_end_date_is_false() {
        val event = Event.from(
            id = "id",
            calendar = Calendar(
                id = "id",
                name = "name",
                email = "email",
                ownerEmail = "ownerEmail",
                description = "description",
                color = "color",
                priority = 0,
                addressId = "addressId",
                memberId = "memberId",
                flags = 1,
                display = true,
                type = 0,
                permissions = 127,
                defaultEventDuration = 30,
                emptyList(),
                emptyList()
            ),
            iCalendar = calendarAllDaySingleDayActualEndDate!!,
            modifyTime = 0
        )!!

        val timezone = "Europe/Zurich"
        val timeFormatIs24Hours = true

        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)
        val formattedDateEnd = event.formatEnd(timezone, timeFormatIs24Hours)

        val expectedResult = "All day event"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_multiple,
                formattedDateStart.first,
                formattedDateEnd.first
            )
        } returns expectedResult

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                expectedResult
            )
        } returns "Test Email Body"

        val result = sendEmailUseCase.getInviteMailBody(
            event = event,
            timezone = timezone,
            timeFormatIs24Hours = timeFormatIs24Hours,
            sendEmailUpdate = false
        )

        assert(result.contains("Test Email Body"))

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_multiple,
                formattedDateStart.first,
                formattedDateEnd.first
            )
        }

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                expectedResult
            )
        }
    }

    @Test
    fun test_getInviteMailBody_with_all_day_event_that_spans_single_day_and_actual_end_date_is_true() {
        val event = Event.from(
            id = "id",
            calendar = Calendar(
                id = "id",
                name = "name",
                email = "email",
                ownerEmail = "ownerEmail",
                description = "description",
                color = "color",
                priority = 0,
                addressId = "addressId",
                memberId = "memberId",
                flags = 1,
                display = true,
                type = 0,
                permissions = 127,
                defaultEventDuration = 30,
                emptyList(),
                emptyList()
            ),
            iCalendar = calendarAllDaySingleDay!!,
            modifyTime = 0
        )!!

        val timezone = "Europe/Zurich"
        val timeFormatIs24Hours = true

        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)
        val formattedDateEnd = event.formatEnd(timezone, timeFormatIs24Hours)

        val expectedResult = "All day event"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_single,
                formattedDateStart.first,
            )
        } returns expectedResult

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                expectedResult
            )
        } returns "Test Email Body"

        val result = sendEmailUseCase.getInviteMailBody(
            event = event,
            timezone = timezone,
            timeFormatIs24Hours = timeFormatIs24Hours,
            sendEmailUpdate = false
        )

        assert(result.contains("Test Email Body"))

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_single,
                formattedDateStart.first,
            )
        }

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                expectedResult
            )
        }
    }

    val calendarAllDaySingleDay = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    DTEND;VALUE=DATE:20200403
    SUMMARY:All-day event on 2nd April
    UID:proton-calendar-11667b13-2041-adde-3bc8-34952f4c0578
    DTSTAMP:20200330T155327Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarAllDaySingleDayActualEndDate = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    DTEND;VALUE=DATE:20200402
    SUMMARY:All-day event on 2nd April, actual end date
    UID:proton-calendar-11667b13-2041-adde-3bc8-34952f4c0578
    DTSTAMP:20200330T155327Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    private val testCalendar = Calendar(
        id = "id",
        name = "name",
        email = "email",
        ownerEmail = "ownerEmail",
        description = "description",
        color = "color",
        priority = 0,
        addressId = "addressId",
        memberId = "memberId",
        flags = 1,
        display = true,
        type = 0,
        permissions = 127,
        defaultEventDuration = 30,
        emptyList(),
        emptyList()
    )

    private val protonMeetUrl = "https://meet.proton.me/join/test-id#pwd-test"

    private val calendarWithProtonMeet = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    DTEND;VALUE=DATE:20200403
    SUMMARY:Meeting with Proton Meet
    UID:proton-calendar-meet-001
    DTSTAMP:20200330T155327Z
    X-PM-CONFERENCE-ID;X-PM-PROVIDER=2:meet-test-id
    X-PM-CONFERENCE-URL:$protonMeetUrl
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    private val zoomUrl = "https://zoom.us/j/123456?pwd=abc"

    private val calendarWithZoom = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    DTEND;VALUE=DATE:20200403
    SUMMARY:Meeting with Zoom
    UID:proton-calendar-zoom-001
    DTSTAMP:20200330T155327Z
    X-PM-CONFERENCE-ID;X-PM-PROVIDER=1:zoom-test-id
    X-PM-CONFERENCE-URL:$zoomUrl
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    @Test
    fun test_getInviteMailBody_with_proton_meet_url_includes_conference_line() {
        val event = Event.from(
            id = "id",
            calendar = testCalendar,
            iCalendar = calendarWithProtonMeet!!,
            modifyTime = 0
        )!!

        val timezone = "Europe/Zurich"
        val timeFormatIs24Hours = true
        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)

        assert(event.meetUrl == protonMeetUrl)
        assert(event.meetType == MeetIntegrationType.ProtonMeet)

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_single,
                formattedDateStart.first
            )
        } returns "All day event"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                "All day event"
            )
        } returns "Base body"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_conference,
                "Join Proton Meet",
                protonMeetUrl
            )
        } returns "\nJoin Proton Meet: $protonMeetUrl"

        val result = sendEmailUseCase.getInviteMailBody(
            event = event,
            timezone = timezone,
            timeFormatIs24Hours = timeFormatIs24Hours,
            sendEmailUpdate = false
        )

        assert(result.contains(protonMeetUrl))

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_conference,
                "Join Proton Meet",
                protonMeetUrl
            )
        }
    }

    @Test
    fun test_getInviteMailBody_with_zoom_url_includes_conference_line() {
        val event = Event.from(
            id = "id",
            calendar = testCalendar,
            iCalendar = calendarWithZoom!!,
            modifyTime = 0
        )!!

        val timezone = "Europe/Zurich"
        val timeFormatIs24Hours = true
        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)

        assert(event.meetUrl == zoomUrl)
        assert(event.meetType == MeetIntegrationType.Zoom)

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_single,
                formattedDateStart.first
            )
        } returns "All day event"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                "All day event"
            )
        } returns "Base body"

        every {
            resourceProviderMock.provideString(R.string.join_zoom_meet_ical_description)
        } returns "Join Zoom Meeting"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_conference,
                "Join Zoom Meeting",
                zoomUrl
            )
        } returns "\nJoin Zoom Meeting: $zoomUrl"

        val result = sendEmailUseCase.getInviteMailBody(
            event = event,
            timezone = timezone,
            timeFormatIs24Hours = timeFormatIs24Hours,
            sendEmailUpdate = false
        )

        assert(result.contains(zoomUrl))

        verify {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_conference,
                "Join Zoom Meeting",
                zoomUrl
            )
        }
    }

    @Test
    fun test_getInviteMailBody_with_meet_url_already_in_description_does_not_duplicate() {
        val event = Event.from(
            id = "id",
            calendar = testCalendar,
            iCalendar = calendarWithProtonMeet!!,
            modifyTime = 0
        )!!

        val timezone = "Europe/Zurich"
        val timeFormatIs24Hours = true
        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_single,
                formattedDateStart.first
            )
        } returns "All day event"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                "All day event"
            )
        } returns "Base body"

        // Simulate description already containing the meet URL (via marker block)
        event.iCalEvent.setDescription("Join Proton Meet: $protonMeetUrl")

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_description,
                event.description
            )
        } returns "\nDescription: Join Proton Meet: $protonMeetUrl"

        val result = sendEmailUseCase.getInviteMailBody(
            event = event,
            timezone = timezone,
            timeFormatIs24Hours = timeFormatIs24Hours,
            sendEmailUpdate = false
        )

        // body already contains the URL via description, so conference line should NOT be added
        verify(exactly = 0) {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_conference,
                any(),
                any()
            )
        }
    }

    @Test
    fun test_getInviteMailBody_without_conference_url_has_no_conference_line() {
        val event = Event.from(
            id = "id",
            calendar = testCalendar,
            iCalendar = calendarAllDaySingleDay!!,
            modifyTime = 0
        )!!

        val timezone = "Europe/Zurich"
        val timeFormatIs24Hours = true
        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)

        assert(event.meetUrl == null)

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_all_day_single,
                formattedDateStart.first
            )
        } returns "All day event"

        every {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body,
                event.summary,
                "All day event"
            )
        } returns "Base body"

        val result = sendEmailUseCase.getInviteMailBody(
            event = event,
            timezone = timezone,
            timeFormatIs24Hours = timeFormatIs24Hours,
            sendEmailUpdate = false
        )

        assert(result == "Base body")

        verify(exactly = 0) {
            resourceProviderMock.provideString(
                R.string.event_send_invite_mail_body_conference,
                any(),
                any()
            )
        }
    }
}