package app.rasoi

import app.rasoi.sos.ChoiceStore
import app.rasoi.sos.Choices
import app.rasoi.sos.LocationDto
import app.rasoi.sos.Locator
import app.rasoi.sos.ReportApi
import app.rasoi.sos.ReportCreated
import app.rasoi.sos.ReportRequest
import app.rasoi.sos.SosStatus
import app.rasoi.sos.SosViewModel
import app.rasoi.sos.StatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SosViewModelTest {
    private class FakeStore(var c: Choices = Choices()) : ChoiceStore {
        override fun load() = c
        override fun save(c: Choices) { this.c = c }
        override fun erase() { c = Choices() }
    }

    private class FakeLocator(val granted: Boolean) : Locator {
        var captures = 0
        override fun permissionGranted() = granted
        override suspend fun capture(): LocationDto? { captures++; return LocationDto(12.9, 77.6, 10.0) }
    }

    private class FakeApi(val post: () -> Response<ReportCreated>) : ReportApi {
        val requests = mutableListOf<ReportRequest>()
        var posts = 0
        override suspend fun post(body: ReportRequest): Response<ReportCreated> { posts++; requests += body; return post() }
        override suspend fun status(id: String): Response<StatusDto> = Response.success(StatusDto("SIMULATED_SENT", "t"))
    }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(api: FakeApi, store: FakeStore = FakeStore(), loc: FakeLocator = FakeLocator(true)) =
        SosViewModel(store, api, loc) { _, _, _ -> }

    @Test fun http200BecomesSentNotReceived() = runTest {
        val api = FakeApi { Response.success(ReportCreated("demo-aaaaaa", "SIMULATED_SENT")) }
        val vm = vm(api)
        vm.open(); vm.setMessage("  LOCK IN ROOM HELP ")
        vm.send(false); advanceUntilIdle()
        assertEquals(SosStatus.SENT, vm.ui.value.status)
        assertEquals("demo-aaaaaa", vm.ui.value.reportId)
        assertEquals("  LOCK IN ROOM HELP ", api.requests.single().originalMessage) // sent unchanged
    }

    @Test fun noLocationUnlessConsented() = runTest {
        val api = FakeApi { Response.success(ReportCreated("demo-aaaaaa", "SIMULATED_SENT")) }
        val loc = FakeLocator(true)
        val vm = vm(api, FakeStore(Choices(setupDone = true, locationConsent = false)), loc)
        vm.open(); vm.setMessage("help"); vm.send(false); advanceUntilIdle()
        assertEquals(0, loc.captures)
        assertNull(api.requests.single().location)
        assertFalse(api.requests.single().locationConsent)
    }

    @Test fun locationAttachedWhenConsentedAndGranted() = runTest {
        val api = FakeApi { Response.success(ReportCreated("demo-aaaaaa", "SIMULATED_SENT")) }
        val vm = vm(api, FakeStore(Choices(setupDone = true, locationConsent = true)))
        vm.open(); vm.setMessage("help"); vm.send(false); advanceUntilIdle()
        assertEquals(12.9, api.requests.single().location?.latitude)
        assertTrue(api.requests.single().locationConsent)
    }

    @Test fun offlineEndsPendingAfterBoundedRetries() = runTest {
        val api = FakeApi { throw IOException("offline") }
        val vm = vm(api)
        vm.open(); vm.setMessage("help"); vm.send(false); advanceUntilIdle()
        assertEquals(SosStatus.PENDING, vm.ui.value.status)
        assertEquals(1 + Config.RETRY_BACKOFF_SECONDS.size, api.posts)
    }

    @Test fun explicitRejectionIsFailedWithoutRetry() = runTest {
        val api = FakeApi { Response.error(422, okhttp3.ResponseBody.create(null, "")) }
        val vm = vm(api)
        vm.open(); vm.setMessage("help"); vm.send(false); advanceUntilIdle()
        assertEquals(SosStatus.FAILED, vm.ui.value.status)
        assertEquals(1, api.posts)
    }

    @Test fun invalidMessageIsNeverSent() = runTest {
        val api = FakeApi { Response.success(ReportCreated("demo-aaaaaa", "SIMULATED_SENT")) }
        val vm = vm(api)
        vm.open(); vm.setMessage("   "); vm.send(false); advanceUntilIdle()
        assertEquals(0, api.posts)
    }

    /** What MainActivity.onStop calls: everything in memory is gone and the session is closed. */
    @Test fun wipeClearsEverything() = runTest {
        val api = FakeApi { Response.success(ReportCreated("demo-aaaaaa", "SIMULATED_SENT")) }
        val vm = vm(api, FakeStore(Choices(setupDone = true, locationConsent = true)))
        vm.open(); vm.setMessage("secret words"); vm.send(false); advanceUntilIdle()
        vm.wipe()
        val s = vm.ui.value
        assertEquals("", s.message)
        assertNull(s.reportId)
        assertEquals(SosStatus.IDLE, s.status)
        assertFalse(s.sessionOpen)
        assertEquals(Choices(), s.choices)
    }

    @Test fun wipeCancelsInFlightRetries() = runTest {
        val api = FakeApi { throw IOException("offline") }
        val vm = vm(api)
        vm.open(); vm.setMessage("help"); vm.send(false)
        testScheduler.runCurrent()
        vm.wipe(); advanceUntilIdle()
        assertEquals(1, api.posts)
        assertEquals(SosStatus.IDLE, vm.ui.value.status)
    }

    @Test fun eraseRemovesStoredChoices() = runTest {
        val store = FakeStore(Choices(setupDone = true, contactEnabled = true, contactPhone = "123"))
        val vm = vm(FakeApi { throw IOException() }, store)
        vm.open(); vm.eraseChoices()
        assertEquals(Choices(), store.c)
    }
}
