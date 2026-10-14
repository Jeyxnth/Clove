# Discreet support reporting: ideathon prototype

An Indian home-cooking recipe app (`/android`) with a hidden report flow, plus a **simulator** backend and responder dashboard (`/backend`).

**Everything responder-side is simulated, with fictional data.** No real helpline, NGO or police service is contacted, and the backend refuses to start unless `MODE=SIMULATION`. AI output is an *advisory urgency category* for human responders; it never blocks, delays or rejects sending. Sending is never a guarantee that anyone receives it. See [DECISIONS.md](DECISIONS.md) for every choice and open item and [TESTING.md](TESTING.md) for the manual checklist.

## Backend

```bash
cd backend
python -m venv .venv && . .venv/Scripts/activate      # Windows; use bin/activate elsewhere
pip install -r requirements.txt
pytest                                                 # 30 tests

# fixture AI (no model needed)
AI_MODE=fixture DASHBOARD_PASSWORD=demo uvicorn app.main:create_app --factory --host 0.0.0.0 --port 8000
```

PowerShell: `$env:AI_MODE="fixture"; $env:DASHBOARD_PASSWORD="demo"; uvicorn app.main:create_app --factory --host 0.0.0.0 --port 8000`

Dashboard: <http://localhost:8000/dashboard> (password `demo` unless you set `DASHBOARD_PASSWORD`).

Env vars: `MODE` (must be `SIMULATION`), `AI_MODE` (`fixture`|`ollama`), `OLLAMA_MODEL` (default `llama3.1:8b`), `OLLAMA_URL`, `DASHBOARD_PASSWORD`, `JWT_SECRET`, `MAX_MESSAGE_CHARS` (280), `RATE_LIMIT_PER_MIN`, `DB_PATH` (default `reports.db`, git-ignored). Put TLS in front of it in any real use.

### Real model (Ollama)

```bash
ollama pull llama3.1:8b
ollama serve                                   # if not already running
AI_MODE=ollama uvicorn app.main:create_app --factory --port 8000
python scripts/ai_eval.py                      # prints category + rationale for HUMAN review
```

If Ollama is down, reports are still stored and shown with `ai_status=UNAVAILABLE`.

## Android

Open `/android` in Android Studio (JDK 17) and let it sync. The Gradle wrapper is committed.

```bash
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest
./gradlew :app:assembleSmsDebug      # optional flavor with SEND_SMS
```

The debug build talks to `http://10.0.2.2:8000` (the emulator's name for your computer). Cleartext to that host is allowed **only in the debug build** by `src/debug/res/xml/network_security_config.xml`. On a real phone change `API_BASE_URL` for debug in `app/build.gradle.kts`. The release URL is a placeholder that never resolves.

The sms flavor has an empty `SMS_RELAY_NUMBER` in `app/build.gradle.kts`; with it empty the SMS path stays disabled.

## 3 minute demo

1. Start the backend in fixture mode, open the dashboard, log in, leave it visible.
2. On the emulator, browse recipes, scale servings on "Rajma Masala", search "paneer".
3. Open any recipe and tap its heart 5 times quickly. The report area opens, and the heart is back where it started.
4. Set up (leave everything off), type `LOCK IN ROOM HELP`, Continue, read the confirmation, Send. Phone says "Submitted to the demo system".
5. On the dashboard the report is at the top with the original text, an "AI-generated" summary and an *advisory* category. Click "Mark received (simulated)".
6. Within 5 s the phone shows "Simulated: Received". Tap Quick exit: you are on Home with nothing left behind.
7. Say plainly what this is not: no real responder, no guarantee, categories are advisory, humans decide.

## Layout

```
backend/   FastAPI simulator, SQLite, dashboard (app/static/dashboard.html), pytest, scripts/ai_eval.py
android/   Kotlin + Compose app, flavors demo (default) and sms
DECISIONS.md  TESTING.md
```
