package app.rasoi.sos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.foundation.text.KeyboardOptions
import app.rasoi.Config
import app.rasoi.R

// All state read here lives in SosViewModel memory or Compose `remember` (never rememberSaveable).
// Dialog/sheet windows use SecureOn because FLAG_SECURE on the activity does not cover separate windows.

private fun dial(context: Context, number: String) =
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
private fun SosFrame(onQuickExit: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onQuickExit) { Text(stringResource(R.string.quick_exit)) }
        }
        content()
    }
}

@Composable
private fun DialRow() {
    val ctx = LocalContext.current
    Column {
        Text(stringResource(R.string.urgent_112), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ dial(ctx, "112") }) { Text(stringResource(R.string.call_112)) }
            OutlinedButton({ dial(ctx, "181") }) { Text(stringResource(R.string.call_181)) }
        }
    }
}

@Composable
private fun SetupForm(initial: Choices, saveLabel: String, onSave: (Choices) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        denied = r.values.none { it }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.s_loc_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(draft.locationConsent, { on ->
                draft = draft.copy(locationConsent = on, contactIncludeLocation = draft.contactIncludeLocation && on)
                denied = false
                if (on) launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            })
        }
        Text(stringResource(R.string.s_loc_body), style = MaterialTheme.typography.bodySmall)
        if (denied && draft.locationConsent) Text(stringResource(R.string.s_loc_denied), color = MaterialTheme.colorScheme.error)

        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.s_contact_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(draft.contactEnabled, { draft = draft.copy(contactEnabled = it) })
        }
        Text(stringResource(R.string.s_contact_body), style = MaterialTheme.typography.bodySmall)
        if (draft.contactEnabled) {
            OutlinedTextField(draft.contactName, { draft = draft.copy(contactName = it) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.s_contact_name)) }, singleLine = true)
            OutlinedTextField(draft.contactPhone, { draft = draft.copy(contactPhone = it) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.s_contact_phone)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(draft.contactText, { draft = draft.copy(contactText = it.take(120)) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.s_contact_text)) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.s_contact_loc), Modifier.weight(1f))
                Switch(draft.contactIncludeLocation && draft.locationConsent, { draft = draft.copy(contactIncludeLocation = it) },
                    enabled = draft.locationConsent)
            }
            if (!SmsBackend.enabled) Text(stringResource(R.string.s_contact_demo), style = MaterialTheme.typography.bodySmall)
        }
        Button(
            { onSave(draft.copy(contactEnabled = draft.contactEnabled && draft.contactPhone.isNotBlank())) },
            Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text(saveLabel) }
    }
}

@Composable
fun SetupScreen(vm: SosViewModel, onDone: () -> Unit, onQuickExit: () -> Unit) {
    val ui by vm.ui.collectAsState()
    BackHandler(onBack = onQuickExit)
    SosFrame(onQuickExit) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.s_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
            SetupForm(ui.choices, stringResource(R.string.s_save)) { vm.saveChoices(it); onDone() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(vm: SosViewModel, onContinue: () -> Unit, onErased: () -> Unit, onQuickExit: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var options by remember { mutableStateOf(false) }
    BackHandler(onBack = onQuickExit)
    SosFrame(onQuickExit) {
        OutlinedTextField(
            ui.message, vm::setMessage, Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            placeholder = { Text(stringResource(R.string.c_hint)) },
        )
        Text(
            stringResource(R.string.c_counter, MessageValidator.length(ui.message), Config.MAX_MESSAGE_CHARS),
            Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall,
        )
        Button(onContinue, Modifier.fillMaxWidth(), enabled = MessageValidator.validate(ui.message) == MessageValidator.Result.OK) {
            Text(stringResource(R.string.c_continue))
        }
        Spacer(Modifier.height(12.dp))
        DialRow()
        TextButton({ options = true }) { Text(stringResource(R.string.options)) }
    }
    if (options) {
        ModalBottomSheet(
            { options = false },
            properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupForm(ui.choices, stringResource(R.string.s_close)) { vm.saveChoices(it); options = false }
                OutlinedButton({ vm.eraseChoices(); options = false; onErased() }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.s_erase))
                }
            }
        }
    }
}

@Composable
fun ConfirmScreen(vm: SosViewModel, onEdit: () -> Unit, onSent: () -> Unit, onQuickExit: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val c = ui.choices
    val ctx = LocalContext.current
    val smsContact = SmsBackend.enabled && c.contactEnabled
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.send(contactSmsAllowed = r.values.all { it })
        onSent()
    }
    BackHandler(onBack = onEdit)
    SosFrame(onQuickExit) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.k_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(if (vm.locationWillBeAttached()) R.string.k_words_loc else R.string.k_words))
            Text(stringResource(R.string.k_to, Config.RECIPIENT_LABEL))
            Text(stringResource(R.string.k_ai, Config.AI_PROVIDER_LABEL))
            if (c.contactEnabled) {
                Text(if (smsContact) stringResource(R.string.k_contact_real, c.contactName.ifBlank { c.contactPhone })
                else stringResource(R.string.k_contact_sim))
            }
            Text(stringResource(R.string.k_no_guarantee), style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                if (smsContact && !SmsBackend.hasPermission(ctx)) permission.launch(SmsBackend.permissions)
                else { vm.send(contactSmsAllowed = smsContact); onSent() }
            }) { Text(stringResource(R.string.k_send)) }
            OutlinedButton(onEdit) { Text(stringResource(R.string.k_edit)) }
        }
        Spacer(Modifier.height(8.dp))
        DialRow()
    }
}

@Composable
fun StatusScreen(vm: SosViewModel, onQuickExit: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var confirmSms by remember { mutableStateOf(false) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) vm.sendSmsFallback()
    }
    LaunchedEffect(Unit) { vm.pollWhileOpen() }
    BackHandler(onBack = onQuickExit)
    val ctx = LocalContext.current

    SosFrame(onQuickExit) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AssistChip({}, label = { Text(stringResource(R.string.t_chip)) })
            Text(
                when (ui.status) {
                    SosStatus.SENDING -> stringResource(R.string.t_sending, ui.attempt.coerceAtLeast(1))
                    SosStatus.SENT -> stringResource(R.string.t_sent)
                    SosStatus.RECEIVED -> stringResource(R.string.t_received)
                    SosStatus.ACKNOWLEDGED -> stringResource(R.string.t_ack)
                    SosStatus.PENDING -> stringResource(R.string.t_pending)
                    SosStatus.FAILED -> stringResource(R.string.t_failed)
                    SosStatus.IDLE -> stringResource(R.string.t_interrupted)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (ui.status != SosStatus.IDLE && ui.choices.locationConsent) {
                Text(stringResource(if (ui.locationAttached) R.string.t_loc_yes else R.string.t_loc_no))
            }
            when (ui.contact) {
                ContactState.SIMULATED -> Text(stringResource(R.string.t_contact_sim))
                ContactState.HANDED_TO_NETWORK -> Text(stringResource(R.string.t_contact_net))
                ContactState.FAILED -> Text(stringResource(R.string.t_contact_fail))
                ContactState.NONE -> {}
            }
            when (ui.sms) {
                SmsEvent.HANDED_TO_NETWORK -> Text(stringResource(R.string.m_handed))
                SmsEvent.DELIVERY_REPORTED -> Text(stringResource(R.string.m_delivered))
                SmsEvent.FAILED -> Text(stringResource(R.string.m_failed))
                null -> {}
            }
            if (ui.status == SosStatus.PENDING || ui.status == SosStatus.FAILED) {
                Button({ vm.send(contactSmsAllowed = false) }) { Text(stringResource(R.string.t_retry)) }
            }
            if (SmsBackend.enabled && ui.smsOffer && ui.sms == null) {
                if (vm.smsConfigured()) OutlinedButton({ confirmSms = true }) { Text(stringResource(R.string.m_offer)) }
                else Text(stringResource(R.string.m_not_configured))
            }
        }
        DialRow()
    }
    if (confirmSms) {
        AlertDialog(
            { confirmSms = false },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.m_confirm_title)) },
            text = { Text(stringResource(R.string.m_confirm_body)) },
            confirmButton = {
                TextButton({
                    confirmSms = false
                    if (SmsBackend.hasPermission(ctx)) vm.sendSmsFallback() else smsPermission.launch(SmsBackend.permissions)
                }) { Text(stringResource(R.string.m_continue)) }
            },
            dismissButton = { TextButton({ confirmSms = false }) { Text(stringResource(R.string.m_cancel)) } },
        )
    }
}
