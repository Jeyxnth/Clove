package app.rasoi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests run with the module directory as the working directory.
 * Source manifests are always checked; the merged demo manifest is also checked when a build has produced it.
 */
class ManifestTest {
    private val sms = "android.permission.SEND_SMS"

    private fun text(path: String) = File(path).takeIf { it.exists() }?.readText().orEmpty()

    @Test fun demoFlavorHasNoSendSms() {
        assertFalse(text("src/main/AndroidManifest.xml").contains(sms))
        assertFalse(text("src/demo/AndroidManifest.xml").contains(sms))
        assertFalse(text("src/debug/AndroidManifest.xml").contains(sms))
        File("build/intermediates").walkTopDown()
            .filter { it.name == "AndroidManifest.xml" && "demo" in it.path.lowercase() && "sms" !in it.path.lowercase().substringAfter("intermediates") }
            .forEach { assertFalse("merged manifest ${it.path} must not declare SEND_SMS", it.readText().contains(sms)) }
    }

    @Test fun smsFlavorDeclaresIt() = assertTrue(text("src/sms/AndroidManifest.xml").contains(sms))

    @Test fun noForbiddenPermissionsAnywhere() {
        val all = listOf("main", "demo", "sms", "debug").joinToString { text("src/$it/AndroidManifest.xml") }
        for (p in listOf("CAMERA", "RECORD_AUDIO", "READ_CONTACTS", "ACCESS_BACKGROUND_LOCATION")) {
            assertFalse("must not request $p", all.contains("android.permission.$p"))
        }
    }

    @Test fun backupDisabled() = assertTrue(text("src/main/AndroidManifest.xml").contains("android:allowBackup=\"false\""))
}
