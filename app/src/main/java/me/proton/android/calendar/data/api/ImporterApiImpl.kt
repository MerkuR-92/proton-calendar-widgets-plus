package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.common.CalendarImport.PRODUCT_CALENDAR
import me.proton.android.calendar.common.CalendarImport.REDIRECT_URI
import me.proton.android.calendar.common.CalendarImport.SOURCE
import me.proton.android.calendar.domain.api.ImporterApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.util.kotlin.toInt
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import javax.inject.Inject


interface ImporterApiService : BaseRetrofitApi {
    @GET("core/v4/system/config")
    suspend fun getGoogleClientId(): GoogleClientIdApiResponse

    @POST("importer/v1/tokens")
    suspend fun createAccessToken(@Body body: CreateAccessTokenApiRequest): CreateAccessTokenApiResponse

    @POST("importer/v1/importers")
    suspend fun createImporter(@Body body: CreateImporterApiRequest): CreateImporterApiResponse

    @GET("importer/v1/calendar/importers/{importerId}")
    suspend fun getCalendarImportMappingInfo(@Path("importerId") importerId: String): CalendarImportMappingInfoApiResponse

    @POST("importer/v1/importers/start")
    suspend fun startImporter(@Body body: StartImporterApiRequest): StartImporterApiResponse
}

class ImporterApiImpl @Inject constructor(private val apiProvider: ApiProvider) : ImporterApi {

    override suspend fun getGoogleClientId(userId: UserId): ApiResponse<GoogleClientIdApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            getGoogleClientId()
        }.toApiResponse()

    override suspend fun createAccessToken(userId: UserId, code: String): ApiResponse<CreateAccessTokenApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            createAccessToken(
                CreateAccessTokenApiRequest(
                    code = code,
                    redirectUri = REDIRECT_URI,
                    source = SOURCE,
                    provider = 1,
                    products = listOf(PRODUCT_CALENDAR)
                )
            )
        }.toApiResponse()

    override suspend fun createCalendarImporter(userId: UserId, tokenId: String): ApiResponse<CreateImporterApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            createImporter(
                CreateImporterApiRequest(
                    tokenId = tokenId,
                    calendar = 1
                )
            )
        }.toApiResponse()

    override suspend fun getCalendarImportMappingInfo(userId: UserId, importerId: String): ApiResponse<CalendarImportMappingInfoApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            getCalendarImportMappingInfo(
                importerId
            )
        }.toApiResponse()

    override suspend fun startImporter(userId: UserId, importerId: String, customCalendarMapping: Boolean, calendarMapping: List<CalendarMappingEntity>): ApiResponse<StartImporterApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            startImporter(
                StartImporterApiRequest(
                    importerId = importerId,
                    calendar = CustomCalendarMappingEntity(
                        customCalendarMapping = customCalendarMapping.toInt(),
                        mapping = calendarMapping
                    )
                )
            )
        }.toApiResponse()
}

@Serializable
data class GoogleClientIdApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Config")
    val config: ConfigEntity
): BaseApiResponse()

@Serializable
data class ConfigEntity(
    @SerialName("importer.google.client_id")
    val googleClientId: String
)

@Serializable
data class CreateAccessTokenApiRequest(
    @SerialName("Code")
    val code: String,
    @SerialName("RedirectUri")
    val redirectUri: String,
    @SerialName("Source")
    val source: String,
    @SerialName("Provider")
    val provider: Int,
    @SerialName("Products")
    val products: List<String>
)

@Serializable
data class CreateAccessTokenApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Token")
    val token: TokenEntity
): BaseApiResponse()

@Serializable
data class TokenEntity(
    @SerialName("ID")
    val id: String,
    @SerialName("Account")
    val account: String,
    @SerialName("Provider")
    val provider: Int,
    @SerialName("Products")
    val products: List<String>
)

@Serializable
data class CreateImporterApiRequest(
    @SerialName("TokenID")
    val tokenId: String,
    @SerialName("Calendar")
    val calendar: Int
)

@Serializable
data class CreateImporterApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("ImporterID")
    val importerID: String
): BaseApiResponse()

@Serializable
data class CalendarImportMappingInfoApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Calendars")
    val calendars: List<ExternalCalendarEntity>
): BaseApiResponse()

@Serializable
data class ExternalCalendarEntity(
    @SerialName("ID")
    val id: String,
    @SerialName("Source")
    val source: String,
    @SerialName("Description")
    val description: String
)

@Serializable
data class StartImporterApiRequest(
    @SerialName("ImporterID")
    val importerId: String,
    @SerialName("Calendar")
    val calendar: CustomCalendarMappingEntity
)

@Serializable
data class CustomCalendarMappingEntity(
    @SerialName("CustomCalendarMapping")
    val customCalendarMapping: Int,
    @SerialName("Mapping")
    val mapping: List<CalendarMappingEntity>
)

@Serializable
data class CalendarMappingEntity(
    @SerialName("Source")
    val source: String,
    @SerialName("Destination")
    val destination: String
)

@Serializable
data class StartImporterApiResponse(
    @SerialName("Code")
    override val code: Int
): BaseApiResponse()
