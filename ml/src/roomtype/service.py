"""FastAPI service. `uv run uvicorn roomtype.service:app --port 8000`

This sits at import time, never in the check path. The compliance check itself must stay
deterministic and reproducible from (geometry, building context, rule set version) alone, and
a model prediction is none of those things. The prediction's job is to pre-fill a field that a
human then reviews and approves, exactly like the LLM rule extraction: a model may propose, a
human disposes, and only the approved value ever reaches a check.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI

from roomtype.predict import Classifier
from roomtype.schema import ClassifyRequest, ClassifyResponse

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="roomtype", version="0.1.0")
_classifier = Classifier()


@app.get("/health")
def health() -> dict[str, object]:
    """The Java side probes this on startup. `model_loaded: false` is a healthy state
    (baseline serving), not a failure, so this returns 200 either way. What it must never do
    is claim a model is loaded when one is not."""
    return {"status": "ok", "model_loaded": _classifier.has_model}


@app.post("/classify", response_model=ClassifyResponse)
def classify(request: ClassifyRequest) -> ClassifyResponse:
    return ClassifyResponse(predictions=[_classifier.predict(room) for room in request.rooms])
