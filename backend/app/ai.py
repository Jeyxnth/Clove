"""AI adapter. Input is message text ONLY; location never reaches this module."""
import json

import httpx

from . import config

CATEGORIES = ("CRITICAL", "HIGH", "MODERATE", "UNCLEAR")


class AIError(Exception):
    pass


# Fixture mode: exact messages -> canned output; everything else is UNCLEAR.
FIXTURES = {
    "LOCK IN ROOM HELP": ("Writer says they are locked in a room and asks for help.", "HIGH",
                          'Quoted: "LOCK IN ROOM HELP"'),
    "He hit me tonight and I am scared to go home": (
        "Writer says they were hit tonight and are afraid to go home.", "CRITICAL",
        'Quoted: "He hit me" and "scared to go home"'),
    "मुझे मदद चाहिए, मेरे पति मुझे घर से निकलने नहीं देते": (
        "Writer asks for help and says their husband does not let them leave the house.", "HIGH",
        'Quoted: "मुझे मदद चाहिए" and "घर से निकलने नहीं देते"'),
    "ghar mein bahut tension hai, kuch baat karni hai": (
        "Writer says there is a lot of tension at home and wants to talk to someone.", "MODERATE",
        'Quoted: "ghar mein bahut tension hai" and "baat karni hai"'),
    "not sure what to write": (
        "Writer is unsure what to write; the text does not describe a situation.", "UNCLEAR",
        'Quoted: "not sure what to write"; no concrete evidence in the text'),
}


def fixture(text: str) -> dict:
    s, c, r = FIXTURES.get(text, (
        "The text does not give enough information to summarise a situation.", "UNCLEAR",
        "No specific words in the text support a category."))
    return {"summary": s, "urgency_category": c, "rationale": r}


SYSTEM_PROMPT = """You prepare short summaries of a short message for human responders at a support organisation.
Rules:
- Summarise only what the text says. Never invent identities, events, relationships or locations.
- Keep uncertainty; do not turn guesses into facts.
- The text may be Hindi, Hinglish or mixed script; understand it, but reply in English.
- Reply with JSON only: {"summary": str, "urgency_category": "CRITICAL"|"HIGH"|"MODERATE"|"UNCLEAR", "rationale": str}
- rationale must quote the exact words from the text that justify the category.
- Use UNCLEAR when the evidence is insufficient. UNCLEAR does not mean safe; never say the person is safe.
- Do not recommend actions to responders. Do not give legal or medical judgements."""


def validate(raw: str) -> dict:
    """Strict check of model JSON. Raises AIError."""
    try:
        d = json.loads(raw)
    except (ValueError, TypeError) as e:
        raise AIError("not json") from e
    if not isinstance(d, dict) or set(d) != {"summary", "urgency_category", "rationale"}:
        raise AIError("wrong keys")
    if d["urgency_category"] not in CATEGORIES:
        raise AIError("bad category")
    if not all(isinstance(d[k], str) and d[k].strip() for k in ("summary", "rationale")):
        raise AIError("bad text fields")
    return d


def call_model(text: str) -> str:
    """One Ollama chat call (20 s timeout). Sends the message text only."""
    try:
        r = httpx.post(config.ollama_url(), timeout=20, json={
            "model": config.ollama_model(), "stream": False, "format": "json",
            "options": {"temperature": 0},
            "messages": [{"role": "system", "content": SYSTEM_PROMPT},
                         {"role": "user", "content": text}]})
        r.raise_for_status()
        return r.json()["message"]["content"]
    except (httpx.HTTPError, KeyError, ValueError) as e:
        raise AIError("model call failed") from e


def analyze(text: str) -> tuple[str, dict | None]:
    """Returns (ai_status, result). Never raises."""
    if config.ai_mode() == "fixture":
        return "FIXTURE_OUTPUT", fixture(text)
    for _ in range(2):  # one retry on failure
        try:
            return "OK", validate(call_model(text))
        except AIError:
            continue
    return "UNAVAILABLE", None
