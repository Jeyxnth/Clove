import base64
import hashlib
import hmac
import json
import re
import secrets
import time
from collections import defaultdict, deque
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Literal

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, ConfigDict, model_validator

from . import ai, config
from .db import Report, make_session_factory, now

MAX_BODY_BYTES = 8192
STATIC = Path(__file__).parent / "static"
CAT_ORDER = {"CRITICAL": 0, "HIGH": 1, "MODERATE": 2, "UNCLEAR": 3}


# ---- request models -------------------------------------------------------
class Location(BaseModel):
    latitude: float
    longitude: float
    accuracy_m: float | None = None

    @model_validator(mode="after")
    def _range(self):
        if not (-90 <= self.latitude <= 90 and -180 <= self.longitude <= 180):
            raise ValueError("coordinates out of range")
        return self


class ReportIn(BaseModel):
    model_config = ConfigDict(extra="ignore")  # a client-sent `mode` is ignored
    original_message: str
    location_consent: bool = False
    location: Location | None = None
    channel: Literal["INTERNET", "SMS_RELAY"] = "INTERNET"

    @model_validator(mode="after")
    def _check(self):
        if not self.original_message.strip():
            raise ValueError("empty")
        if len(self.original_message) > config.max_chars():
            raise ValueError("too long")
        if self.location is not None and not self.location_consent:
            raise ValueError("location without consent")
        return self


class Login(BaseModel):
    password: str


class Mark(BaseModel):
    status: Literal["SIMULATED_RECEIVED", "SIMULATED_ACKNOWLEDGED"]


class SmsIn(BaseModel):
    payload: str


# ---- tokens (stdlib HMAC; ponytail: single shared demo password, swap for real auth/JWT lib if ever productionised)
def make_token() -> str:
    body = base64.urlsafe_b64encode(json.dumps({"exp": time.time() + 8 * 3600}).encode()).decode()
    sig = hmac.new(config.jwt_secret().encode(), body.encode(), hashlib.sha256).hexdigest()
    return f"{body}.{sig}"


def token_ok(tok: str) -> bool:
    try:
        body, sig = tok.rsplit(".", 1)
        good = hmac.new(config.jwt_secret().encode(), body.encode(), hashlib.sha256).hexdigest()
        return hmac.compare_digest(sig, good) and json.loads(base64.urlsafe_b64decode(body))["exp"] > time.time()
    except Exception:
        return False


def require_auth(authorization: str = Header(default="")):
    if not (authorization.startswith("Bearer ") and token_ok(authorization[7:])):
        raise HTTPException(401, "unauthorized")


# ---- app ------------------------------------------------------------------
def create_app() -> FastAPI:
    if config.mode() != "SIMULATION":
        raise RuntimeError("MODE must be SIMULATION; production delivery is deliberately not implemented")

    sf = make_session_factory(config.db_path())
    pool = ThreadPoolExecutor(max_workers=2)  # AI runs here so sending never waits for it
    hits: dict[str, deque] = defaultdict(deque)
    app = FastAPI(title="Support reporting SIMULATOR", docs_url=None, redoc_url=None)
    app.state.pool = pool
    app.state.sf = sf

    def rate_limit(request: Request):
        ip = request.client.host if request.client else "?"
        q, t = hits[ip], time.time()
        while q and q[0] < t - 60:
            q.popleft()
        if len(q) >= config.rate_limit():
            raise HTTPException(429, "slow down")
        q.append(t)

    @app.middleware("http")
    async def cap_payload(request: Request, call_next):
        if int(request.headers.get("content-length") or 0) > MAX_BODY_BYTES:
            return JSONResponse({"detail": "too large"}, status_code=413)
        return await call_next(request)

    @app.exception_handler(RequestValidationError)
    async def _invalid(request, exc):  # generic: never echo the body back or into logs
        return JSONResponse({"detail": "invalid request"}, status_code=422)

    def run_ai(rid: str, text: str):
        status, res = ai.analyze(text)  # module attr so tests can patch it
        with sf() as s:
            r = s.get(Report, rid)
            if r is None:  # deleted meanwhile
                return
            r.ai_status = status
            if res:
                r.ai_summary, r.ai_category, r.ai_rationale = res["summary"], res["urgency_category"], res["rationale"]
            s.commit()

    def new_report(msg: str, channel: str, loc: Location | None = None, rid: str | None = None,
                   synthetic: bool = False) -> Report:
        r = Report(id=rid or "demo-" + secrets.token_hex(3), original_message=msg, channel=channel,
                   latitude=loc.latitude if loc else None, longitude=loc.longitude if loc else None,
                   accuracy_m=loc.accuracy_m if loc else None, synthetic=synthetic)
        with sf() as s:
            s.add(r)
            s.commit()
        pool.submit(run_ai, r.id, msg)
        return r

    @app.get("/healthz")
    def healthz():
        return {"ok": True, "mode": "SIMULATION"}

    @app.post("/v1/reports", status_code=201, dependencies=[Depends(rate_limit)])
    def post_report(body: ReportIn):
        r = new_report(body.original_message, body.channel, body.location)
        return {"id": r.id, "delivery_status": r.delivery_status}

    @app.get("/v1/reports/{rid}/status", dependencies=[Depends(rate_limit)])
    def status(rid: str):
        with sf() as s:
            r = s.get(Report, rid)
        if r is None:
            raise HTTPException(404, "not found")
        return {"delivery_status": r.delivery_status, "updated_at": r.updated_at.isoformat()}

    @app.post("/v1/sms-relay", status_code=201, dependencies=[Depends(rate_limit)])
    def sms_relay(body: SmsIn):
        # SOS1|<id>|<message>|<lat,lon optional>   (demo gateway simulator only)
        parts = body.payload.split("|")
        if len(parts) < 3 or parts[0] != "SOS1":
            raise HTTPException(422, "invalid request")
        rid, rest = parts[1], parts[2:]
        loc = None
        m = re.fullmatch(r"(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)", rest[-1]) if len(rest) > 1 else None
        if m:
            loc, rest = Location(latitude=float(m[1]), longitude=float(m[2])), rest[:-1]
        msg = "|".join(rest)
        ReportIn(original_message=msg, location_consent=loc is not None, location=loc, channel="SMS_RELAY")
        if not re.fullmatch(r"demo-[0-9a-f]{6}", rid):
            rid = None
        with sf() as s:
            if rid and s.get(Report, rid):  # already arrived over the internet
                return {"id": rid}
        return {"id": new_report(msg, "SMS_RELAY", loc, rid).id}

    # ---- dashboard --------------------------------------------------------
    @app.post("/dashboard/login")
    def login(body: Login):
        if not hmac.compare_digest(body.password.encode(), config.dashboard_password().encode()):
            raise HTTPException(401, "unauthorized")
        return {"token": make_token()}

    def view(r: Report) -> dict:
        return {"id": r.id, "created_at": r.created_at.isoformat(), "original_message": r.original_message,
                "ai_summary": r.ai_summary, "urgency_category": r.ai_category, "rationale": r.ai_rationale,
                "ai_status": r.ai_status, "delivery_status": r.delivery_status, "channel": r.channel,
                "location": None if r.latitude is None else
                {"latitude": r.latitude, "longitude": r.longitude, "accuracy_m": r.accuracy_m}}

    @app.get("/dashboard/reports", dependencies=[Depends(require_auth)])
    def list_reports():
        with sf() as s:
            rows = s.query(Report).order_by(Report.created_at.desc()).all()
        rows.sort(key=lambda r: CAT_ORDER.get(r.ai_category, 4))  # stable: newest first within a category
        return [view(r) for r in rows]

    @app.post("/dashboard/reports/{rid}/mark", dependencies=[Depends(require_auth)])
    def mark(rid: str, body: Mark):
        with sf() as s:
            r = s.get(Report, rid)
            if r is None:
                raise HTTPException(404, "not found")
            r.delivery_status, r.updated_at = body.status, now()
            s.commit()
        return {"ok": True}

    @app.delete("/dashboard/reports/{rid}", dependencies=[Depends(require_auth)])
    def delete(rid: str):
        with sf() as s:
            r = s.get(Report, rid)
            if r:
                s.delete(r)
                s.commit()
        return {"ok": True}

    @app.post("/dashboard/reset-all", dependencies=[Depends(require_auth)])
    def reset_all():
        with sf() as s:
            s.query(Report).delete()
            s.commit()
        return {"ok": True}

    @app.post("/dashboard/seed", dependencies=[Depends(require_auth)])
    def seed():
        for msg in ai.FIXTURES:
            new_report(msg, "INTERNET", synthetic=True)
        return {"ok": True, "count": len(ai.FIXTURES)}

    @app.get("/dashboard", include_in_schema=False)
    def page():
        return FileResponse(STATIC / "dashboard.html")

    return app
