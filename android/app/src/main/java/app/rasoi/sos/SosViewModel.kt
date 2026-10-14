package app.rasoi.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rasoi.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ContactState { NONE, SIMULATED, HANDED_TO_NETWORK, FAILED }

/** Everything here lives in ViewModel memory only: never SavedStateHandle, files, logs or notifications. */
data class SosUi(
    val sessionOpen: Boolean = false,
    val choices: Choices = Choices(),
    val message: String = "",
    val status: SosStatus = SosStatus.IDLE,
    val reportId: String? = null,
    val attempt: Int = 0,
    val locationAttached: Boolean = false,
    val smsOffer: Boolean = false,
    val sms: SmsEvent? = null,
    val contact: ContactState = ContactState.NONE,
)

class SosViewModel(
    private val prefs: ChoiceStore,
    private val api: ReportApi,
    private val location: Locator,
    private val sendSms: SmsSender,
) : ViewModel() {
    private val _ui = MutableStateFlow(SosUi())
    val ui: StateFlow<SosUi> = _ui.asStateFlow()

    private var sendJob: Job? = null
    private var lastLocation: LocationDto? = null
    private var smsReportId: String? = null

    fun open() {
        _ui.value = SosUi(sessionOpen = true, choices = prefs.load())
    }

    /** Clears every trace of the session from memory. Setup choices stay in encrypted storage. */
    fun wipe() {
        sendJob?.cancel()
        sendJob = null
        lastLocation = null
        smsReportId = null
        _ui.value = SosUi()
    }

    fun saveChoices(c: Choices) {
        val saved = c.copy(setupDone = true)
        prefs.save(saved)
        _ui.update { it.copy(choices = saved) }
    }

    fun eraseChoices() {
        prefs.erase()
        _ui.update { it.copy(choices = Choices()) }
    }

    fun setMessage(text: String) = _ui.update { it.copy(message = MessageValidator.clamp(text)) }

    fun locationWillBeAttached() = _ui.value.choices.locationConsent && location.permissionGranted()

    /** [contactSmsAllowed]: the sms flavor asked for SEND_SMS and got it. Ignored in the demo flavor. */
    fun send(contactSmsAllowed: Boolean) {
        val s = _ui.value
        if (MessageValidator.validate(s.message) != MessageValidator.Result.OK) return
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            _ui.update { it.copy(status = SosStatus.SENDING, smsOffer = false, attempt = 0, reportId = null) }
            launch {
                delay(Config.SMS_OFFER_AFTER_MS)
                if (_ui.value.status == SosStatus.SENDING) _ui.update { it.copy(smsOffer = true) }
            }
            val loc = if (locationWillBeAttached()) location.capture() else null
            lastLocation = loc
            _ui.update { it.copy(locationAttached = loc != null) }
            notifyContact(contactSmsAllowed, loc)

            val request = ReportRequest(s.message, locationConsent = loc != null, location = loc)
            val attempts = Config.RETRY_BACKOFF_SECONDS.size + 1
            for (i in 0 until attempts) {
                _ui.update { it.copy(attempt = i + 1) }
                val outcome = attemptPost(request)
                if (outcome != null) {
                    _ui.update { it.copy(status = outcome.first, reportId = outcome.second ?: it.reportId) }
                    if (outcome.first != SosStatus.PENDING) return@launch
                }
                if (i < attempts - 1) delay(Config.RETRY_BACKOFF_SECONDS[i] * 1000)
            }
            _ui.update { it.copy(status = SosStatus.PENDING, smsOffer = true) }
        }
    }

    /** Status and report id, or null when the network failed outright. 2xx is SENT, never RECEIVED. */
    private suspend fun attemptPost(request: ReportRequest): Pair<SosStatus, String?>? = try {
        val r = api.post(request)
        val body = r.body()
        when {
            r.isSuccessful && body != null -> StatusMapper.fromHttp(r.code()) to body.id
            r.isSuccessful -> SosStatus.PENDING to null // accepted but unreadable answer: not confirmed
            else -> StatusMapper.fromHttp(r.code()) to null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun notifyContact(allowed: Boolean, loc: LocationDto?) {
        val c = _ui.value.choices
        if (!c.contactEnabled || c.contactPhone.isBlank()) return
        if (!SmsBackend.enabled) {
            _ui.update { it.copy(contact = ContactState.SIMULATED) }
            return
        }
        if (!allowed) return
        val text = c.contactText + if (c.contactIncludeLocation && loc != null) " geo:${loc.latitude},${loc.longitude}" else ""
        sendSms(c.contactPhone, text) { ev ->
            if (_ui.value.sessionOpen && ev != SmsEvent.DELIVERY_REPORTED) {
                _ui.update { it.copy(contact = if (ev == SmsEvent.FAILED) ContactState.FAILED else ContactState.HANDED_TO_NETWORK) }
            }
        }
    }

    fun smsConfigured() = SmsBackend.enabled && SmsBackend.relayNumber.isNotBlank()

    /** Only called after the second, explicit confirmation and the permission grant. */
    fun sendSmsFallback() {
        if (!smsConfigured()) return
        val s = _ui.value
        val id = smsReportId ?: SmsPayload.newReportId().also { smsReportId = it }
        val loc = lastLocation
        val payload = SmsPayload.build(id, s.message, loc?.latitude, loc?.longitude)
        sendSms(SmsBackend.relayNumber, payload) { ev ->
            if (_ui.value.sessionOpen) _ui.update { it.copy(sms = ev) }
        }
    }

    /** Runs while the report screen is composed. Polls every [Config.STATUS_POLL_MS]. */
    suspend fun pollWhileOpen() {
        while (true) {
            delay(Config.STATUS_POLL_MS)
            val id = _ui.value.reportId ?: continue
            try {
                val r = api.status(id)
                val body = r.body()
                if (r.isSuccessful && body != null) {
                    _ui.update { if (it.reportId == id) it.copy(status = StatusMapper.fromDelivery(body.deliveryStatus)) else it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // keep the last known status; a failed poll is not news
            }
        }
    }
}
