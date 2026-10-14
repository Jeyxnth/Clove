import logging
import threading
import time

import pytest

from app import ai
from app.main import create_app
from conftest import wait_ai

MSGS = ["LOCK IN ROOM HELP", "मुझे मदद चाहिए, मेरे पति मुझे घर से निकलने नहीं देते",
        "ghar mein bahut tension hai, kuch baat karni hai", "not sure what to write"]


def post(client, **kw):
    return client.post("/v1/reports", json={"original_message": "hello", **kw})


@pytest.mark.parametrize("body", [
    {"original_message": ""}, {"original_message": "   \n"}, {"original_message": "x" * 281},
    {"original_message": "a", "location_consent": False, "location": {"latitude": 1, "longitude": 1}},
    {"original_message": "a", "location_consent": True, "location": {"latitude": 91, "longitude": 1}},
    {"original_message": "a", "location_consent": True, "location": {"latitude": 1, "longitude": 181}},
    {"original_message": "a", "channel": "CARRIER_PIGEON"},
])
def test_validation_rejects(client, body):
    assert client.post("/v1/reports", json=body).status_code == 422


def test_280_chars_unicode_ok(client):
    assert client.post("/v1/reports", json={"original_message": "म" * 280}).status_code == 201


def test_client_mode_ignored(client):
    r = post(client, mode="PRODUCTION")
    assert r.status_code == 201 and "mode" not in r.json()


def test_refuses_non_simulation_mode(monkeypatch):
    monkeypatch.setenv("MODE", "PRODUCTION")
    with pytest.raises(RuntimeError):
        create_app()


def test_status_has_no_message(client):
    rid = post(client).json()["id"]
    assert rid.startswith("demo-")
    s = client.get(f"/v1/reports/{rid}/status").json()
    assert set(s) == {"delivery_status", "updated_at"} and s["delivery_status"] == "SIMULATED_SENT"
    assert client.get("/v1/reports/nope/status").status_code == 404


def test_sending_does_not_wait_for_ai(client, auth, monkeypatch):
    gate = threading.Event()
    monkeypatch.setattr(ai, "analyze", lambda t: (gate.wait(10), ("FIXTURE_OUTPUT", ai.fixture(t)))[1])
    t = time.time()
    assert post(client).status_code == 201
    assert time.time() - t < 1
    row = client.get("/dashboard/reports", headers=auth).json()[0]
    assert row["ai_status"] == "PENDING"  # still stored and visible while AI "hangs"
    gate.set()
    assert wait_ai(client, auth)[0]["ai_status"] == "FIXTURE_OUTPUT"


def test_ai_parse_failure_is_unavailable(client, auth, monkeypatch):
    monkeypatch.setenv("AI_MODE", "ollama")
    monkeypatch.setattr(ai, "call_model", lambda t: "not json at all")
    post(client)
    row = wait_ai(client, auth)[0]
    assert row["ai_status"] == "UNAVAILABLE" and row["urgency_category"] is None
    assert row["original_message"] == "hello"


@pytest.mark.parametrize("raw", [
    '{"summary":"s","urgency_category":"SAFE","rationale":"r"}',
    '{"summary":"s","urgency_category":"HIGH"}',
    '{"summary":"","urgency_category":"HIGH","rationale":"r"}',
    '[1]'])
def test_strict_validation(raw):
    with pytest.raises(ai.AIError):
        ai.validate(raw)


def test_empty_unclear_is_valid_but_empty_high_is_not():
    d = ai.validate('{"summary":"","urgency_category":"UNCLEAR","rationale":""}')
    assert d["urgency_category"] == "UNCLEAR" and d["summary"] and d["rationale"]
    with pytest.raises(ai.AIError):
        ai.validate('{"summary":"","urgency_category":"CRITICAL","rationale":""}')


def test_ai_retries_once(client, auth, monkeypatch):
    monkeypatch.setenv("AI_MODE", "ollama")
    calls = []
    good = '{"summary":"s","urgency_category":"UNCLEAR","rationale":"r"}'
    monkeypatch.setattr(ai, "call_model", lambda t: calls.append(1) or ("bad" if len(calls) == 1 else good))
    post(client)
    assert wait_ai(client, auth)[0]["ai_status"] == "OK" and len(calls) == 2


def test_end_to_end_five_messages(client, auth):
    for m in MSGS:
        assert client.post("/v1/reports", json={"original_message": m}).status_code == 201
    assert client.post("/v1/reports", json={"original_message": ""}).status_code == 422
    rows = wait_ai(client, auth)
    assert len(rows) == 4
    assert {r["original_message"] for r in rows} == set(MSGS)  # byte-identical
    assert rows[-1]["urgency_category"] == "UNCLEAR"  # sorted last, not styled/treated as safe


def test_whitespace_preserved(client, auth):
    m = "  help\n\tme  "
    post(client, original_message=m)
    assert wait_ai(client, auth)[0]["original_message"] == m


def test_location_only_with_consent(client, auth):
    post(client)
    post(client, location_consent=True, location={"latitude": 12.9, "longitude": 77.6, "accuracy_m": 20})
    rows = {bool(r["location"]) for r in wait_ai(client, auth)}
    assert rows == {True, False}


def test_unknown_message_is_unclear(client, auth):
    post(client, original_message="something else entirely")
    assert wait_ai(client, auth)[0]["urgency_category"] == "UNCLEAR"


def test_no_message_in_logs(client, auth, caplog):
    caplog.set_level(logging.DEBUG)
    secret = "ZEBRA-SECRET-TEXT-123"
    post(client, original_message=secret)
    client.post("/v1/reports", json={"original_message": ""})
    wait_ai(client, auth)
    assert secret not in caplog.text


def test_dashboard_auth_required(client, auth):
    rid = post(client).json()["id"]
    for method, path in [("get", "/dashboard/reports"), ("post", f"/dashboard/reports/{rid}/mark"),
                         ("delete", f"/dashboard/reports/{rid}"), ("post", "/dashboard/reset-all"),
                         ("post", "/dashboard/seed")]:
        assert getattr(client, method)(path).status_code == 401
        assert getattr(client, method)(path, headers={"Authorization": "Bearer x.y"}).status_code == 401
    assert client.post("/dashboard/login", json={"password": "wrong"}).status_code == 401


def test_mark_and_status_roundtrip(client, auth):
    rid = post(client).json()["id"]
    assert client.post(f"/dashboard/reports/{rid}/mark", headers=auth, json={"status": "SIMULATED_RECEIVED"}).status_code == 200
    assert client.get(f"/v1/reports/{rid}/status").json()["delivery_status"] == "SIMULATED_RECEIVED"
    assert client.post(f"/dashboard/reports/{rid}/mark", headers=auth, json={"status": "FAILED"}).status_code == 422


def test_seed_delete_reset(client, auth):
    client.post("/dashboard/seed", headers=auth)
    rows = wait_ai(client, auth)
    assert len(rows) == 5
    client.delete(f"/dashboard/reports/{rows[0]['id']}", headers=auth)
    assert len(client.get("/dashboard/reports", headers=auth).json()) == 4
    client.post("/dashboard/reset-all", headers=auth)
    assert client.get("/dashboard/reports", headers=auth).json() == []


def test_rate_limit(client, monkeypatch):
    monkeypatch.setenv("RATE_LIMIT_PER_MIN", "3")
    codes = [post(client).status_code for _ in range(5)]
    assert codes == [201, 201, 201, 429, 429]


def test_payload_cap(client):
    assert client.post("/v1/reports", json={"original_message": "a" * 20000}).status_code == 413


def test_sms_relay(client, auth):
    r = client.post("/v1/sms-relay", json={"payload": "SOS1|demo-abc123|LOCK IN ROOM HELP|12.9,77.6"})
    assert r.status_code == 201 and r.json()["id"] == "demo-abc123"
    client.post("/v1/sms-relay", json={"payload": "SOS1|demo-abc123|LOCK IN ROOM HELP"})  # duplicate: no 2nd row
    rows = wait_ai(client, auth)
    assert len(rows) == 1 and rows[0]["location"]["latitude"] == 12.9 and rows[0]["channel"] == "SMS_RELAY"
    assert client.post("/v1/sms-relay", json={"payload": "junk"}).status_code == 422


def test_dashboard_page_and_health(client):
    assert client.get("/healthz").json()["mode"] == "SIMULATION"
    html = client.get("/dashboard").text
    assert "SIMULATION: fictional demo data" in html and "innerHTML" not in html
