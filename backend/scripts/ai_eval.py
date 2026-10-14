"""Run the synthetic set through the real Ollama model and print results for HUMAN review.
Needs Ollama running:  ollama pull llama3.1:8b   then   python scripts/ai_eval.py
Deliberately asserts nothing: hallucination absence cannot be checked automatically."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
os.environ["AI_MODE"] = "ollama"
sys.stdout.reconfigure(encoding="utf-8")

from app import ai  # noqa: E402

SET = list(ai.FIXTURES) + [
    "LOCK IN ROOM HELP",
    "he said he will take my phone away if I tell anyone",
    "mera bhai mujhe bahar jaane nahi deta aur maarta hai",
    "ok",
]

for text in dict.fromkeys(SET):
    status, res = ai.analyze(text)
    print("=" * 70)
    print("MESSAGE :", text)
    print("AI      :", status)
    if res:
        print("CATEGORY:", res["urgency_category"], "(advisory)")
        print("SUMMARY :", res["summary"])
        print("RATIONALE:", res["rationale"])
print("\nReview by hand: does each summary add anything not in the text? Is UNCLEAR used when evidence is thin?")
