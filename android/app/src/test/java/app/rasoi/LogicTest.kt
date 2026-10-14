package app.rasoi

import app.rasoi.data.Scaler
import app.rasoi.sos.MessageValidator
import app.rasoi.sos.MessageValidator.Result
import app.rasoi.sos.SmsPayload
import app.rasoi.sos.SosStatus
import app.rasoi.sos.StatusMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    // ---- status mapping: accepted by the server is never "received" ----
    @Test fun http200IsSentNotReceived() {
        assertEquals(SosStatus.SENT, StatusMapper.fromHttp(200))
        assertEquals(SosStatus.SENT, StatusMapper.fromHttp(201))
        assertNotEquals(SosStatus.RECEIVED, StatusMapper.fromHttp(200))
        assertNotEquals(SosStatus.ACKNOWLEDGED, StatusMapper.fromHttp(200))
    }

    @Test fun httpErrors() {
        assertEquals(SosStatus.FAILED, StatusMapper.fromHttp(422))
        assertEquals(SosStatus.PENDING, StatusMapper.fromHttp(429))
        assertEquals(SosStatus.PENDING, StatusMapper.fromHttp(503))
    }

    @Test fun deliveryStatuses() {
        assertEquals(SosStatus.SENT, StatusMapper.fromDelivery("SIMULATED_SENT"))
        assertEquals(SosStatus.RECEIVED, StatusMapper.fromDelivery("SIMULATED_RECEIVED"))
        assertEquals(SosStatus.ACKNOWLEDGED, StatusMapper.fromDelivery("SIMULATED_ACKNOWLEDGED"))
        assertEquals(SosStatus.PENDING, StatusMapper.fromDelivery("PENDING"))
        assertEquals(SosStatus.FAILED, StatusMapper.fromDelivery("FAILED"))
        assertEquals(SosStatus.PENDING, StatusMapper.fromDelivery("SOMETHING_NEW"))
    }

    // ---- message validation ----
    @Test fun validation() {
        assertEquals(Result.EMPTY, MessageValidator.validate(""))
        assertEquals(Result.EMPTY, MessageValidator.validate("  \n "))
        assertEquals(Result.OK, MessageValidator.validate("x".repeat(280)))
        assertEquals(Result.TOO_LONG, MessageValidator.validate("x".repeat(281)))
    }

    @Test fun unicodeCountsByCodePoint() {
        assertEquals(Result.OK, MessageValidator.validate("मदद ".repeat(70))) // 280 code points
        assertEquals(Result.OK, MessageValidator.validate("😀".repeat(280))) // 560 UTF-16 units, 280 code points
        assertEquals(Result.TOO_LONG, MessageValidator.validate("😀".repeat(281)))
        assertEquals(Result.OK, MessageValidator.validate("Ghar mein problem hai, madad karo"))
        assertEquals(280, MessageValidator.length(MessageValidator.clamp("😀".repeat(400))))
    }

    @Test fun clampKeepsShortTextUntouched() {
        val s = "  help me \n"
        assertEquals(s, MessageValidator.clamp(s))
    }

    // ---- recipe scaler ----
    @Test fun scalerMaths() {
        assertEquals(2.0, Scaler.scale(1.0, 2, 4), 1e-9)
        assertEquals(0.75, Scaler.scale(1.5, 4, 2), 1e-9)
        assertEquals(7.5, Scaler.scale(2.5, 2, 6), 1e-9)
    }

    @Test fun scalerFormatting() {
        assertEquals("½", Scaler.format(0.5))
        assertEquals("2¼", Scaler.format(2.25))
        assertEquals("3", Scaler.format(3.0))
        assertEquals("1⅓", Scaler.format(4.0 / 3))
        assertEquals("1", Scaler.format(0.99))
        assertEquals("45", Scaler.format(45.2))
        assertEquals("0.1", Scaler.format(0.1))
    }

    @Test fun millilitreDisplay() {
        assertEquals(30.0 to "ml", Scaler.display(2.0, "tbsp", true))
        assertEquals(2.0 to "tbsp", Scaler.display(2.0, "tbsp", false))
        assertEquals(200.0 to "g", Scaler.display(200.0, "g", true))
    }

    // ---- sms payload ----
    @Test fun smsPayload() {
        assertEquals("SOS1|demo-abc123|help", SmsPayload.build("demo-abc123", "help", null, null))
        assertEquals("SOS1|demo-abc123|help|12.9,77.6", SmsPayload.build("demo-abc123", "help", 12.9, 77.6))
        assertTrue(Regex("demo-[0-9a-f]{6}").matches(SmsPayload.newReportId()))
    }
}
