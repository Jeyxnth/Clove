# Decisions and open items

This is an ideathon **demonstration prototype**. Nothing here contacts a real responder, helpline, NGO or police service.

## What persists on the phone (complete list)

| Data | Where | When |
|---|---|---|
| Favourite recipe ids | DataStore (`recipe_prefs`) | always |
| Theme, "show millilitres" | DataStore (`recipe_prefs`) | always |
| Setup choices (location consent, trusted contact name/phone/text, "setup done" flag) | `EncryptedSharedPreferences`, file name `x_store`, AES-256 via Android Keystore | only after she saves the setup card; "Erase these choices" deletes it |

**Never persisted:** message text, location fix, report id, delivery status, AI output, SMS payload. These live in `SosViewModel` memory only. No `SavedStateHandle`, `rememberSaveable`, draft, file, clipboard, notification or log is used for them. Process death clears them by nature; there is deliberately no "clear on next launch" mechanism.

If the Keystore is unavailable, `SosPrefs` falls back to memory only (setup is asked again next time). It never falls back to plaintext.

Backup: `allowBackup=false`, plus `dataExtractionRules` and `fullBackupContent` excluding every domain.

## Behaviour choices

- **Trigger window.** Five taps on the same recipe's heart within 10 s *of the first tap of the sequence*. A longer pause therefore also restarts the sequence (covers "10 s of inactivity" as well). Counter is a plain object in `remember`, reset when the recipe screen is left. `TRIGGER_TAPS` / `TRIGGER_WINDOW_MS` are in `Config.kt`. **These are placeholders pending usability and accidental-activation testing.**
- **Favourite restore.** Taps 1 to 4 toggle the heart normally (it is a real favourite). On tap 5 the favourite is set back to its state before tap 1, not toggled. If the app is killed in the middle of a sequence the heart may be left toggled; accepted.
- **No transitions.** Every `NavHost` transition is `None`, so quick exit and screen changes show no animation that reveals the previous screen.
- **Quick exit** (button, and system Back from compose, setup and status): `wipe()` then `navigate(home)` popping the whole stack, synchronously.
- **Leaving the app clears the session.** `onStop` (unless it is a rotation) wipes SOS memory state and goes to recipe Home. This includes tapping *Call 112 / Call 181*, because the dialer backgrounds the app. We accept that: making a call matters more than keeping an unsent draft. She must retype afterwards.
- **"App interrupted" state.** After a wipe she lands on recipe Home and sees nothing, because any hint on Home would reveal the feature. The "This was interrupted" text only appears if the status screen is somehow reached without a live session (for example a restored back stack after process death). The 112/181 buttons are on every report screen, not only on failure states.
- **Retry.** First attempt, then retries after 2, 5 and 10 s (so at most 4 POSTs), only while the screen is open. A manual "Try again" is offered in `PENDING` / `FAILED`. A retry after a lost response can create a duplicate report on the demo backend; there is no idempotency key yet.
- **Open trade-off:** an unsent report is lost if the app is closed before sending. Saving drafts would be a worse risk on a shared phone.
- **HTTP mapping.** 2xx is `SENT` ("Submitted to the demo system"). 4xx other than 408/429 is `FAILED`. Network errors, 5xx, 408 and 429 end in `PENDING` ("Not confirmed"). `RECEIVED` / `ACKNOWLEDGED` appear only when the status endpoint reports that the simulated responder marked it. Unknown backend values map to `PENDING`.
- **Location.** Consent is its own stored flag, not inferred from the Android permission. The permission is requested only when she turns the switch on. The fix is captured once, when she presses Send, on API 30+ via `getCurrentLocation` (5 s timeout); below API 30 only a last-known fix is used. If consent is on but no fix can be had, the report is sent without location and the status screen says so. Location never enters the AI prompt (the AI adapter receives the message string only).
- **Dialogs and sheets use `SecureFlagPolicy.SecureOn`** because `FLAG_SECURE` on the activity does not cover separate dialog windows. `FLAG_SECURE` is set when the heart pattern completes (before the first report frame) and cleared when she is back on a recipe screen.
- **Character limit** counts Unicode code points (so it matches Python's `len` on the backend). The message is sent exactly as typed, not trimmed.
- **Units setting** is "show spoons and cups as millilitres" (5 / 15 / 240 ml). Ingredient names are not converted; grams stay grams.
- **Recipe content is English only**, UI strings are English and Hindi. Hindi strings were written for the prototype and need review by a native speaker before any pilot.
- **Python 3.10** was what the dev machine had; the code needs nothing newer than 3.10 although the brief says 3.11+.

## Backend choices

- `MODE` must be `SIMULATION` or the app refuses to start. A client-sent `mode` field is ignored (`extra="ignore"`).
- `POST /v1/reports` returns 201 immediately; AI runs on a 2-worker thread pool afterwards. A test holds the AI "hung" and checks the POST returns in under a second and the report is already visible.
- Dashboard auth is one shared demo password and an HMAC-signed bearer token (stdlib, 8 h expiry). Not production auth.
- Rate limit is an in-memory per-IP sliding window; fine for a single-process demo only. The payload cap checks the `Content-Length` header only; a chunked body without it is still bounded by the 280-character validation but not by the cap.
- Validation errors return a generic 422 body so message text is never echoed.
- Original text is stored and returned byte-for-byte; the dashboard writes it with `textContent` only.
- The AI prompt, JSON schema and strict validation are in `app/ai.py`. Parse failure is retried once, then `ai_status=UNAVAILABLE`. A category is never invented when the model is down.
- Seed data is the five fixture messages (English, Hindi, Hinglish, ambiguous, one more English). Fixture mode maps exactly these strings, everything else is `UNCLEAR` and flagged `FIXTURE_OUTPUT`.

## SMS flavor

- `sms` flavor alone declares `SEND_SMS`. The demo flavor has no SMS code path that can run (`SmsBackend.enabled = false`).
- `SMS_RELAY_NUMBER` is empty by default, so the UI says "SMS backup is not configured" and nothing is sent. **TODO(config).**
- SMS is offered after 10 s of `SENDING` or when the attempts end in `PENDING`, behind a second confirmation, and the permission is requested only at that moment. Its result is only ever shown as "Handed to your mobile network" / "Network reports delivery to the number", never as `RECEIVED`.
- **Safety limitation:** Android keeps sent SMS in the default messaging app's database or the telephony provider. The app cannot reliably remove that record, and the carrier bill may show it. The confirmation says so.
- The trusted-contact SMS (sms flavor only, only if she opted in) is a short neutral editable line. Location is appended as `geo:lat,lon` only if she turned on both location sharing and "include my location".
- The relay payload `SOS1|<id>|<message>|<lat,lon>` is parsed by `POST /v1/sms-relay`, which exists for the gateway simulator only.

## Known gaps

- **The APK itself reveals the feature** to anyone who decompiles it (class and string resource names). Obfuscation and string hiding are out of scope for the prototype; only the running UI is discreet.
- A local `adb` user, a rooted phone, or someone watching the screen is outside the threat model of this prototype.
- Accidental activation rate of the 5-tap pattern is untested. So is discoverability for the people it is meant for.
- Real recipient channel, real AI provider (and its data-processing terms), app name and icon: not decided, left as config / TODO.
