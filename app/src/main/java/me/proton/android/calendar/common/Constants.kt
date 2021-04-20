package me.proton.android.calendar.common

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

const val API_VERSION_CALENDAR = "v1"
//const val API_BASE_URL = "https://protonmail.blue/api/"
const val API_BASE_URL = "https://api.protonmail.ch/api/"
const val API_APPLICATION_NAME = "AndroidCalendar"

const val USER_AGENT_NAME = "ProtonCalendar"

const val OFFLINE_EVENT_ID_PREFIX = "Proton-Android-App-Offline-Event-ID:"
const val OFFLINE_ALARM_ID_PREFIX = "Proton-Android-App-Offline-Alarm-ID:"
const val ATTENDEE_AUTO_EXPAND_LIMIT = 5

const val CLICK_INTERVAL_MS: Long = 500L

const val WORKER_MAX_RETRY_COUNT = 5

val SYNC_EVENTS_IN_APP_REFRESH_PERIOD = Duration.ofSeconds(30)
val SYNC_EVENTS_PERIODIC_REFRESH_PERIOD = Duration.ofHours(1)
val SYNC_EVENTS_PERIODIC_DELAY_START = Duration.ofMinutes(5)

val SYNC_CALENDARS_DELAY = Duration.ofSeconds(3)
val UPDATE_PASSPHRASE_CALENDARS_DELAY = Duration.ofSeconds(5)

// TODO change this also in Navigation.kt
const val DEEPLINK_PATH_EVENT_DETAILS = "proton-calendar://protonmail.com/event_details/"
const val DEEPLINK_PATH_EVENT_EDIT = "proton-calendar://protonmail.com/event/edit?eventId="
const val DEEPLINK_PATH_EVENT_CREATE = "proton-calendar://protonmail.com/event/create"

const val DEFAULT_CALENDAR_COLOR = "#657EE4"

const val INVITE_ICS_FILE_NAME = "invite.ics"
const val INVITE_ICS_MIME_TYPE = "text/calendar"
const val INVITE_EMAIL_MIME_TYPE = "text/plain"

const val ICAL_LINE_MAXIMUM_LENGTH = 75
const val ICAL_LINE_SEPARATOR = "\\r\\n "
const val ICAL_UID_PREFIX = "UID:"

const val MAX_ANIM_DURATION = 500L

const val CONTACTS_SEARCH_QUERY = "CONTACTS_SEARCH_QUERY"

val PROTON_MAIL_DOMAINS = arrayListOf("protonmail.ch", "protonmail.com", "pm.me")

val PROTON_UID = "@proton.me"
val PROTON_OLD_UID = "proton-calendar"

const val MAX_EMAILS_PER_QUERY: Int = 8

object FeatureFlag {
    const val NEW_EVENT_DECRYPTION = true
    const val SETTINGS_DRAWER = true
    const val ADD_ATTENDEES = false
    const val CHANGE_ANSWER = false
}

object ApiResponseCode {
    const val EVENT_DOES_NOT_EXIST = 2501
}

object SharedPreferencesKeys {
    const val THEME = "theme"
    const val SHOW_CONTACTS_PERMISSIONS_DIALOG = "show_contacts_permissions_dialog"
}

enum class AppTheme(val value: Int) {
    LIGHT(0),
    DARK(1),
    SYSTEM_DEFAULT(2)
}

object IcsParsingValidation {
    const val UID_MAX_LENGTH = 191
    const val SUMMARY_MAX_LENGTH = 255
    const val LOCATION_MAX_LENGTH = 255
    const val DESCRIPTION_MAX_LENGTH = 3000

    const val MAX_COUNT = 49
    const val MAX_COUNT_INVITATION = 499
    const val MAX_DAILY_INTERVAL = 999
    const val MAX_WEEKLY_INTERVAL = 4999
    const val MAX_MONTHLY_INTERVAL = 999
    const val MAX_YEARLY_INTERVAL = 99
    const val MAX_ATTENDEES = 100

    val MIN_DATE = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
    val MAX_DATE = ZonedDateTime.of(2038, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))

    const val X_WR_TIMEZONE = "X-WR-TIMEZONE"
    const val TZID = "TZID"
}

object FormValidation {

    val MIN_SUPPORTED_DATETIME = LocalDate.of(1970, 1, 1).atStartOfDay(ZoneId.of("UTC"))
    val MAX_SUPPORTED_DATETIME = LocalDate.of(2038, 12, 31).atStartOfDay(ZoneId.of("UTC"))

    const val OCCURRENCE_COUNT_DEFAULT = 2
    const val OCCURRENCE_COUNT_MIN = 1
    const val OCCURRENCE_COUNT_MAX = 49

    const val INTERVAL_DAY_COUNT_DEFAULT = 1
    const val INTERVAL_DAY_COUNT_MIN = 1
    const val INTERVAL_DAY_COUNT_MAX = 999

    const val INTERVAL_WEEK_COUNT_DEFAULT = 1
    const val INTERVAL_WEEK_COUNT_MIN = 1
    const val INTERVAL_WEEK_COUNT_MAX = 4999

    const val INTERVAL_MONTH_COUNT_DEFAULT = 1
    const val INTERVAL_MONTH_COUNT_MIN = 1
    const val INTERVAL_MONTH_COUNT_MAX = 999

    const val INTERVAL_YEAR_COUNT_DEFAULT = 1
    const val INTERVAL_YEAR_COUNT_MIN = 1
    const val INTERVAL_YEAR_COUNT_MAX = 99

    // TODO use this when saving/editing/IMPORTING Event
    const val EVENT_SUMMARY_MAX_LENGTH = 255
    const val EVENT_LOCATION_MAX_LENGTH = 255
    const val EVENT_DESCRIPTION_MAX_LENGTH = 3000


    const val ALARM_COUNT_MAX = 10

    const val ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT = 1
    const val ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT = 15
    const val ALARM_PERIOD_COUNT_MIN = 1

    const val ALARM_PERIOD_MAX_WEEKS = 999
    const val ALARM_PERIOD_MAX_DAYS = 6999
    const val ALARM_PERIOD_MAX_HOURS = 999
    const val ALARM_PERIOD_MAX_MINUTES = 9999

    const val ATTENDEE_SHOW_TRESHOLD = 5

    const val ATTENDEE_MAX_ALLOWED = 100
    const val ATTENDEE_MAX_CHIP_ALLOWED = 4

}

object FragmentArguments {
    const val POSITION_ARG = "POSITION_ARG"
    const val DATE_ARG = "DATE_ARG"
}

object CustomICalPropertyParameter {
    const val X_PM_TOKEN = "X-PM-TOKEN"
    const val X_PM_SESSION_KEY = "X-PM-SESSION-KEY"
    const val X_PM_SHARED_EVENT_ID = "X-PM-SHARED-EVENT-ID"
}

object MessageDigestHashType {
    const val SHA1 = "SHA-1"
}

val allowedTimezoneIds = listOf(
    "Africa/Abidjan",
    "Africa/Accra",
    "Africa/Algiers",
    "Africa/Bissau",
    "Africa/Cairo",
    "Africa/Casablanca",
    "Africa/Ceuta",
    "Africa/El_Aaiun",
    "Africa/Johannesburg",
    "Africa/Juba",
    "Africa/Khartoum",
    "Africa/Lagos",
    "Africa/Maputo",
    "Africa/Monrovia",
    "Africa/Nairobi",
    "Africa/Ndjamena",
    "Africa/Sao_Tome",
    "Africa/Tripoli",
    "Africa/Tunis",
    "Africa/Windhoek",
    "America/Adak",
    "America/Anchorage",
    "America/Araguaina",
    "America/Argentina/Buenos_Aires",
    "America/Argentina/Catamarca",
    "America/Argentina/Cordoba",
    "America/Argentina/Jujuy",
    "America/Argentina/La_Rioja",
    "America/Argentina/Mendoza",
    "America/Argentina/Rio_Gallegos",
    "America/Argentina/Salta",
    "America/Argentina/San_Juan",
    "America/Argentina/San_Luis",
    "America/Argentina/Tucuman",
    "America/Argentina/Ushuaia",
    "America/Asuncion",
    "America/Atikokan",
    "America/Bahia",
    "America/Bahia_Banderas",
    "America/Barbados",
    "America/Belem",
    "America/Belize",
    "America/Blanc-Sablon",
    "America/Boa_Vista",
    "America/Bogota",
    "America/Boise",
    "America/Cambridge_Bay",
    "America/Campo_Grande",
    "America/Cancun",
    "America/Caracas",
    "America/Cayenne",
    "America/Chicago",
    "America/Chihuahua",
    "America/Costa_Rica",
    "America/Creston",
    "America/Cuiaba",
    "America/Curacao",
    "America/Danmarkshavn",
    "America/Dawson",
    "America/Dawson_Creek",
    "America/Denver",
    "America/Detroit",
    "America/Edmonton",
    "America/Eirunepe",
    "America/El_Salvador",
    "America/Fort_Nelson",
    "America/Fortaleza",
    "America/Glace_Bay",
    "America/Godthab",
    "America/Goose_Bay",
    "America/Grand_Turk",
    "America/Guatemala",
    "America/Guayaquil",
    "America/Guyana",
    "America/Halifax",
    "America/Havana",
    "America/Hermosillo",
    "America/Indiana/Knox",
    "America/Indiana/Marengo",
    "America/Indiana/Petersburg",
    "America/Indiana/Tell_City",
    "America/Indiana/Vevay",
    "America/Indiana/Vincennes",
    "America/Indiana/Winamac",
    "America/Inuvik",
    "America/Iqaluit",
    "America/Jamaica",
    "America/Juneau",
    "America/Kentucky/Louisville",
    "America/Kentucky/Monticello",
    "America/La_Paz",
    "America/Lima",
    "America/Los_Angeles",
    "America/Maceio",
    "America/Managua",
    "America/Manaus",
    "America/Martinique",
    "America/Matamoros",
    "America/Mazatlan",
    "America/Menominee",
    "America/Merida",
    "America/Metlakatla",
    "America/Mexico_City",
    "America/Miquelon",
    "America/Moncton",
    "America/Monterrey",
    "America/Montevideo",
    "America/Nassau",
    "America/New_York",
    "America/Nipigon",
    "America/Nome",
    "America/Noronha",
    "America/North_Dakota/Beulah",
    "America/North_Dakota/Center",
    "America/North_Dakota/New_Salem",
    "America/Ojinaga",
    "America/Panama",
    "America/Pangnirtung",
    "America/Paramaribo",
    "America/Phoenix",
    "America/Port-au-Prince",
    "America/Port_of_Spain",
    "America/Porto_Velho",
    "America/Puerto_Rico",
    "America/Punta_Arenas",
    "America/Rainy_River",
    "America/Rankin_Inlet",
    "America/Recife",
    "America/Regina",
    "America/Resolute",
    "America/Rio_Branco",
    "America/Santarem",
    "America/Santiago",
    "America/Santo_Domingo",
    "America/Sao_Paulo",
    "America/Scoresbysund",
    "America/Sitka",
    "America/St_Johns",
    "America/Swift_Current",
    "America/Tegucigalpa",
    "America/Thule",
    "America/Thunder_Bay",
    "America/Tijuana",
    "America/Toronto",
    "America/Vancouver",
    "America/Whitehorse",
    "America/Winnipeg",
    "America/Yakutat",
    "America/Yellowknife",
    "Antarctica/Casey",
    "Antarctica/Davis",
    "Antarctica/DumontDUrville",
    "Antarctica/Macquarie",
    "Antarctica/Mawson",
    "Antarctica/Palmer",
    "Antarctica/Rothera",
    "Antarctica/Syowa",
    "Antarctica/Troll",
    "Antarctica/Vostok",
    "Asia/Almaty",
    "Asia/Amman",
    "Asia/Anadyr",
    "Asia/Aqtau",
    "Asia/Aqtobe",
    "Asia/Ashgabat",
    "Asia/Atyrau",
    "Asia/Baghdad",
    "Asia/Baku",
    "Asia/Bangkok",
    "Asia/Barnaul",
    "Asia/Beirut",
    "Asia/Bishkek",
    "Asia/Brunei",
    "Asia/Chita",
    "Asia/Choibalsan",
    "Asia/Colombo",
    "Asia/Damascus",
    "Asia/Dhaka",
    "Asia/Dili",
    "Asia/Dubai",
    "Asia/Dushanbe",
    "Asia/Famagusta",
    "Asia/Gaza",
    "Asia/Hebron",
    "Asia/Ho_Chi_Minh",
    "Asia/Hong_Kong",
    "Asia/Hovd",
    "Asia/Irkutsk",
    "Asia/Jakarta",
    "Asia/Jayapura",
    "Asia/Jerusalem",
    "Asia/Kabul",
    "Asia/Kamchatka",
    "Asia/Karachi",
    "Asia/Kathmandu",
    "Asia/Khandyga",
    "Asia/Kolkata",
    "Asia/Krasnoyarsk",
    "Asia/Kuala_Lumpur",
    "Asia/Kuching",
    "Asia/Macau",
    "Asia/Magadan",
    "Asia/Makassar",
    "Asia/Manila",
    "Asia/Nicosia",
    "Asia/Novokuznetsk",
    "Asia/Novosibirsk",
    "Asia/Omsk",
    "Asia/Oral",
    "Asia/Pontianak",
    "Asia/Pyongyang",
    "Asia/Qatar",
    "Asia/Qostanay",
    "Asia/Qyzylorda",
    "Asia/Riyadh",
    "Asia/Sakhalin",
    "Asia/Samarkand",
    "Asia/Seoul",
    "Asia/Shanghai",
    "Asia/Srednekolymsk",
    "Asia/Taipei",
    "Asia/Tashkent",
    "Asia/Tbilisi",
    "Asia/Tehran",
    "Asia/Thimphu",
    "Asia/Tokyo",
    "Asia/Tomsk",
    "Asia/Ulaanbaatar",
    "Asia/Urumqi",
    "Asia/Ust-Nera",
    "Asia/Vladivostok",
    "Asia/Yakutsk",
    "Asia/Yekaterinburg",
    "Asia/Yerevan",
    "Atlantic/Azores",
    "Atlantic/Bermuda",
    "Atlantic/Canary",
    "Atlantic/Cape_Verde",
    "Atlantic/Faroe",
    "Atlantic/Madeira",
    "Atlantic/Reykjavik",
    "Atlantic/South_Georgia",
    "Atlantic/Stanley",
    "Australia/Adelaide",
    "Australia/Brisbane",
    "Australia/Broken_Hill",
    "Australia/Currie",
    "Australia/Darwin",
    "Australia/Eucla",
    "Australia/Hobart",
    "Australia/Lindeman",
    "Australia/Lord_Howe",
    "Australia/Melbourne",
    "Australia/Perth",
    "Australia/Sydney",
    "Europe/Amsterdam",
    "Europe/Andorra",
    "Europe/Astrakhan",
    "Europe/Athens",
    "Europe/Belgrade",
    "Europe/Berlin",
    "Europe/Brussels",
    "Europe/Bucharest",
    "Europe/Budapest",
    "Europe/Chisinau",
    "Europe/Copenhagen",
    "Europe/Dublin",
    "Europe/Gibraltar",
    "Europe/Helsinki",
    "Europe/Istanbul",
    "Europe/Kaliningrad",
    "Europe/Kiev",
    "Europe/Kirov",
    "Europe/Lisbon",
    "Europe/London",
    "Europe/Luxembourg",
    "Europe/Madrid",
    "Europe/Malta",
    "Europe/Minsk",
    "Europe/Monaco",
    "Europe/Moscow",
    "Europe/Oslo",
    "Europe/Paris",
    "Europe/Prague",
    "Europe/Riga",
    "Europe/Rome",
    "Europe/Samara",
    "Europe/Saratov",
    "Europe/Simferopol",
    "Europe/Sofia",
    "Europe/Stockholm",
    "Europe/Tallinn",
    "Europe/Tirane",
    "Europe/Ulyanovsk",
    "Europe/Uzhgorod",
    "Europe/Vienna",
    "Europe/Vilnius",
    "Europe/Volgograd",
    "Europe/Warsaw",
    "Europe/Zaporozhye",
    "Europe/Zurich",
    "Indian/Chagos",
    "Indian/Christmas",
    "Indian/Cocos",
    "Indian/Kerguelen",
    "Indian/Mahe",
    "Indian/Maldives",
    "Indian/Mauritius",
    "Indian/Reunion",
    "Pacific/Apia",
    "Pacific/Auckland",
    "Pacific/Bougainville",
    "Pacific/Chatham",
    "Pacific/Chuuk",
    "Pacific/Easter",
    "Pacific/Efate",
    "Pacific/Enderbury",
    "Pacific/Fakaofo",
    "Pacific/Fiji",
    "Pacific/Galapagos",
    "Pacific/Gambier",
    "Pacific/Guadalcanal",
    "Pacific/Guam",
    "Pacific/Honolulu",
    "Pacific/Kiritimati",
    "Pacific/Kosrae",
    "Pacific/Kwajalein",
    "Pacific/Majuro",
    "Pacific/Marquesas",
    "Pacific/Nauru",
    "Pacific/Niue",
    "Pacific/Norfolk",
    "Pacific/Noumea",
    "Pacific/Pago_Pago",
    "Pacific/Palau",
    "Pacific/Pitcairn",
    "Pacific/Pohnpei",
    "Pacific/Port_Moresby",
    "Pacific/Rarotonga",
    "Pacific/Tahiti",
    "Pacific/Tarawa",
    "Pacific/Tongatapu",
    "UTC"
)
