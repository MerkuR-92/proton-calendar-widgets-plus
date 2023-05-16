package me.proton.android.calendar.common.utils

import me.proton.core.featureflag.domain.entity.FeatureId

enum class CalendarFeatureFlag(val featureId: FeatureId, val defaultLocalValue: Boolean = false, val isLocalFlag: Boolean = true) {

    // Naming convention for remote feature flags:
    //  - Features shared among different products: {FeatureName}{Platform}{Product} (ex: RatingAndroidCalendar)
    //  - Others: {Product}{Platform}{FeatureName} (ex: CalendarAndroidHoliday)

    // Remote flags
    CalendarAndroidHoliday(FeatureId("CalendarAndroidHoliday"), false, false),

    // Local only flag (unknown to remote API)
    // Enabled
    AddAttendees(FeatureId("AddAttendees"), true),
    ChangeAnswer(FeatureId("ChangeAnswer"), true),
    OpenInvitation(FeatureId("OpenInvitation"), true),
    AppLinks(FeatureId("AppLinks"), true),
    DeleteCalendar(FeatureId("DeleteCalendar"), true),
    ChangeCalendarSimpleEvent(FeatureId("ChangeCalendarSimpleEvent"), true),
    UseEventDecryptor(FeatureId("UseEventDecryptor"), true),
    ChangeLanguage(FeatureId("ChangeLanguage"), true),
    MonthView(FeatureId("MonthView"), true),
    Feedback(FeatureId("Feedback"), true),
    AutoInvitesSetting(FeatureId("AutoInvitesSetting"), true),
    ImportAssistant(FeatureId("ImportAssistant"), true),
    ThreeDaysView(FeatureId("ThreeDaysView"), true),
    WeekView(FeatureId("WeekView"), true),
    ImportIcs(FeatureId("ImportIcs"), true),
    EditingSharedCalendars(FeatureId("EditingSharedCalendars"), true),
    ShowEventSearch(FeatureId("ShowEventSearch"), true),

    // Disabled
    Subscription(FeatureId("Subscription"), false),
    ShowSignatureVerificationBadges(FeatureId("ShowSignatureVerificationBadges"), false),
    DragAndDrop(FeatureId("DragAndDrop"), false),
    ClearCalendar(FeatureId("ClearCalendar"), false)
}
