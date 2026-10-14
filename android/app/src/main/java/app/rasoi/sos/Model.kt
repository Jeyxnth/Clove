package app.rasoi.sos

import app.rasoi.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

enum class SosStatus { IDLE, SENDING, SENT, RECEIVED, ACKNOWLEDGED, PENDING, FAILED }

enum class SmsEvent { HANDED_TO_NETWORK, DELIVERY_REPORTED, FAILED }

object StatusMapper {
    /** Backend delivery_status -> app state. Unknown values are never treated as confirmation. */
    fun fromDelivery(s: String): SosStatus = when (s) {
        "SIMULATED_SENT" -> SosStatus.SENT
        "SIMULATED_RECEIVED" -> SosStatus.RECEIVED
        "SIMULATED_ACKNOWLEDGED" -> SosStatus.ACKNOWLEDGED
        "FAILED" -> SosStatus.FAILED
        else -> SosStatus.PENDING
    }

    /** HTTP result of POST /v1/reports. A 2xx is SENT (accepted), never RECEIVED. */
    fun fromHttp(code: Int): SosStatus = when (code) {
        in 200..299 -> SosStatus.SENT
        408, 429 -> SosStatus.PENDING
        in 400..499 -> SosStatus.FAILED
        else -> SosStatus.PENDING
    }
}

object MessageValidator {
    enum class Result { OK, EMPTY, TOO_LONG }

    /** Counts Unicode code points, matching the backend's character count. */
    fun length(text: String) = text.codePointCount(0, text.length)

    fun validate(text: String): Result = when {
        text.isBlank() -> Result.EMPTY
        length(text) > Config.MAX_MESSAGE_CHARS -> Result.TOO_LONG
        else -> Result.OK
    }

    fun clamp(text: String): String =
        if (length(text) <= Config.MAX_MESSAGE_CHARS) text
        else text.substring(0, text.offsetByCodePoints(0, Config.MAX_MESSAGE_CHARS))
}

object SmsPayload {
    fun build(reportId: String, message: String, lat: Double?, lon: Double?): String =
        "SOS1|$reportId|$message" + if (lat != null && lon != null) "|$lat,$lon" else ""

    fun newReportId(): String = "demo-" + (1..6).map { "0123456789abcdef".random() }.joinToString("")
}

@Serializable
data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_m") val accuracyM: Double? = null,
)

@Serializable
data class ReportRequest(
    @SerialName("original_message") val originalMessage: String,
    @SerialName("location_consent") val locationConsent: Boolean,
    val location: LocationDto? = null,
    val channel: String = "INTERNET",
)

@Serializable
data class ReportCreated(val id: String, @SerialName("delivery_status") val deliveryStatus: String)

@Serializable
data class StatusDto(
    @SerialName("delivery_status") val deliveryStatus: String,
    @SerialName("updated_at") val updatedAt: String,
)

interface ReportApi {
    @POST("v1/reports")
    suspend fun post(@Body body: ReportRequest): Response<ReportCreated>

    @GET("v1/reports/{id}/status")
    suspend fun status(@Path("id") id: String): Response<StatusDto>
}

/** What she chose in setup. Contains personal data, so it only ever lives in encrypted storage. */
data class Choices(
    val setupDone: Boolean = false,
    val locationConsent: Boolean = false,
    val contactEnabled: Boolean = false,
    val contactName: String = "",
    val contactPhone: String = "",
    val contactText: String = Config.DEFAULT_CONTACT_TEXT,
    val contactIncludeLocation: Boolean = false,
)

interface ChoiceStore {
    fun load(): Choices
    fun save(c: Choices)
    fun erase()
}

interface Locator {
    fun permissionGranted(): Boolean
    suspend fun capture(): LocationDto?
}

typealias SmsSender = (to: String, text: String, onEvent: (SmsEvent) -> Unit) -> Unit
