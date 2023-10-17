package me.proton.android.calendar.common.utils

import me.proton.core.featureflag.domain.entity.FeatureId

enum class CalendarFeatureFlag(val featureId: FeatureId, val fallbackValue: Boolean, val isLocalFlag: Boolean) {

    // Naming convention for remote feature flags:
    //  - Features shared among different products: {FeatureName}{Platform}{Product} (ex: RatingAndroidCalendar)
    //  - Others: {Product}{Platform}{FeatureName} (ex: CalendarAndroidHoliday)

    // Remote flags
    CalendarAndroidHoliday(FeatureId("CalendarAndroidHoliday"), false, false),
    CalendarAndroidAutoAddHoliday(FeatureId("CalendarAndroidAutoAddHoliday"), false, false),
    RatingAndroidCalendar(FeatureId("RatingAndroidCalendar"), false, false),
    CalendarAndroidServerDownBanner(FeatureId("CalendarAndroidServerDownBanner"), false, false),

    // Local only flag (unknown to remote API)
    // Enabled
    AddAttendees(FeatureId("AddAttendees"), true, true),
    ChangeAnswer(FeatureId("ChangeAnswer"), true, true),
    OpenInvitation(FeatureId("OpenInvitation"), true, true),
    AppLinks(FeatureId("AppLinks"), true, true),
    DeleteCalendar(FeatureId("DeleteCalendar"), true, true),
    ChangeCalendarSimpleEvent(FeatureId("ChangeCalendarSimpleEvent"), true, true),
    UseEventDecryptor(FeatureId("UseEventDecryptor"), true, true),
    ChangeLanguage(FeatureId("ChangeLanguage"), true, true),
    MonthView(FeatureId("MonthView"), true, true),
    Feedback(FeatureId("Feedback"), true, true),
    AutoInvitesSetting(FeatureId("AutoInvitesSetting"), true, true),
    ImportAssistant(FeatureId("ImportAssistant"), true, true),
    ThreeDaysView(FeatureId("ThreeDaysView"), true, true),
    WeekView(FeatureId("WeekView"), true, true),
    ImportIcs(FeatureId("ImportIcs"), true, true),
    EditingSharedCalendars(FeatureId("EditingSharedCalendars"), true, true),
    EditInvitationAsOrganizer(FeatureId("EditInvitationAsOrganizer"), false, true),
    Payments(FeatureId("Subscription"), false, true),
    EditAttendeesAsOrganizer(FeatureId("EditAttendeesAsOrganizer"), true, true),

    // Disabled
    ShowSignatureVerificationBadges(FeatureId("ShowSignatureVerificationBadges"), false, true),
    DragAndDrop(FeatureId("DragAndDrop"), false, true),
    ClearCalendar(FeatureId("ClearCalendar"), false, true),
    ShowEventSearch(FeatureId("ShowEventSearch"), false, true)
}
