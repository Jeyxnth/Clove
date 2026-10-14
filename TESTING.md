# Manual test checklist

Run the backend (fixture mode) first, see README. Use an emulator or a phone on the same network. Tick each box.

## Trigger and flow
- [ ] Open a recipe, tap the heart once: it becomes a favourite. Tap again: removed. Nothing else happens.
- [ ] Tap the heart 4 times quickly: no SOS screen, heart is back to its original state after tap 4 (even count).
- [ ] Tap 5 times on one recipe within 10 s: report area opens with no animation, no toast, no vibration. Go back to the recipe: the heart is **in the same state as before** the 5 taps.
- [ ] 5 taps spread across two recipes: nothing opens.
- [ ] 5 taps with a pause of more than 10 s inside: nothing opens.
- [ ] Settings, Search, Favourites, Home: no mention of SOS, location sharing or trusted contacts anywhere (also with TalkBack on).
- [ ] First open shows the setup card; location and trusted contact are both OFF.
- [ ] Type "LOCK IN ROOM HELP", Continue, check the confirmation text, Send. Status shows "Submitted to the demo system" and the SIMULATION chip.
- [ ] On the dashboard click "Mark received (simulated)". Within about 5 s the phone shows "Simulated: Received".

## Quick exit
- [ ] Quick exit from setup, compose, confirmation, status: lands on recipe Home immediately (under about 300 ms, no transition, back stack empty: pressing Back leaves the app).
- [ ] System Back from compose does the same. Re-enter the report area: message box is empty.

## Rotation, app switch, force stop
- [ ] Rotate on compose: typed text is kept (same session). Rotate on status: status kept.
- [ ] Type text, press Home / use app switcher: on return you are on recipe Home, and re-entering shows an empty message.
- [ ] In the app switcher the report screens show a blank thumbnail (FLAG_SECURE). Recipe screens show normally.
- [ ] Force stop the app from Settings mid-compose: relaunch shows recipe Home, nothing remembered.
- [ ] Tap "Call 112": the dialer opens with the number filled in and **does not dial**. Returning to the app lands on Home.

## Offline
- [ ] Airplane mode on, send: status goes `SENDING` (attempt counter rises) then "Not confirmed. If it is urgent, call 112." with Try again and the 112 / 181 buttons.
- [ ] Airplane mode off, Try again: becomes "Submitted to the demo system".
- [ ] Stop the backend and send: same as offline, never shows Received.

## Screenshot and secure windows
- [ ] Try a screenshot on every report screen, and on the Options sheet and the SMS confirmation dialog: blocked or black.
- [ ] Screen recording shows black for report screens.

## Location
- [ ] With location OFF, send: dashboard shows no location. No permission prompt ever appeared.
- [ ] Turn location ON in setup: permission prompt appears only then. Deny it: the card says no location will be attached; send works and the status says "Location was not attached".
- [ ] Grant it and send: dashboard shows coordinates. The AI summary does not mention them.

## Trusted contact and options
- [ ] Demo build: with contact enabled, the confirmation and status say "Simulated". Nothing is sent.
- [ ] Options sheet: change choices, then "Erase these choices": next open shows the setup card again.

## sms flavor (`assembleSmsDebug`, set `SMS_RELAY_NUMBER` first)
- [ ] With the number empty: offline send shows "SMS backup is not configured".
- [ ] With a number: after about 10 s offline, "Send by SMS instead" appears. Tapping shows the second confirmation; only then is the SMS permission requested.
- [ ] Result shows "Handed to your mobile network", never "Received".
- [ ] demo flavor: `adb shell dumpsys package app.rasoi | grep SEND_SMS` returns nothing.

## Backup exclusion and leftovers
- [ ] `adb shell run-as app.rasoi ls -R .` (debug build): no message text anywhere; `grep -r "LOCK IN ROOM" .` finds nothing. `x_store.xml` is unreadable ciphertext.
- [ ] `adb bugreport` / `adb logcat -d | grep -i "lock in room"` returns nothing.
- [ ] `adb backup` (or `bmgr`) produces no app data.
- [ ] The notification shade and clipboard contain nothing from the report.
