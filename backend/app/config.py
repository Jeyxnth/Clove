"""Config from env only. Read at call time so tests can monkeypatch."""
import os


def env(name: str, default: str) -> str:
    return os.environ.get(name, default)


def mode() -> str:
    return env("MODE", "SIMULATION")


def ai_mode() -> str:
    return env("AI_MODE", "fixture")


def ollama_model() -> str:
    return env("OLLAMA_MODEL", "llama3.1:8b")


def ollama_url() -> str:
    return env("OLLAMA_URL", "http://localhost:11434/api/chat")


def dashboard_password() -> str:
    return env("DASHBOARD_PASSWORD", "demo")


def jwt_secret() -> str:
    return env("JWT_SECRET", "dev-only-change-me")


def max_chars() -> int:
    return int(env("MAX_MESSAGE_CHARS", "280"))


def rate_limit() -> int:
    return int(env("RATE_LIMIT_PER_MIN", "30"))


def db_path() -> str:
    return env("DB_PATH", "reports.db")
