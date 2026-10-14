import time

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("DB_PATH", str(tmp_path / "t.db"))
    monkeypatch.setenv("AI_MODE", "fixture")
    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "1000")
    monkeypatch.setenv("DASHBOARD_PASSWORD", "pw")
    with TestClient(create_app()) as c:
        yield c


@pytest.fixture
def auth(client):
    t = client.post("/dashboard/login", json={"password": "pw"}).json()["token"]
    return {"Authorization": f"Bearer {t}"}


def wait_ai(client, auth, timeout=5):
    """Poll the dashboard until no report has ai_status PENDING."""
    end = time.time() + timeout
    while time.time() < end:
        rows = client.get("/dashboard/reports", headers=auth).json()
        if rows and all(r["ai_status"] != "PENDING" for r in rows):
            return rows
        time.sleep(0.05)
    raise AssertionError("AI never finished")
