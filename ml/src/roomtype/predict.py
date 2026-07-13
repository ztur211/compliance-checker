"""Serving-time prediction: the trained model if there is one, the baseline otherwise.

The fallback is not a nicety. This service sits in the import path of a compliance tool, and
the alternative to "degrade to keyword matching" is "the import fails". Degrading loudly and
usefully beats failing, as long as the caller is told which happened, which is what the
`source` field on every prediction is for.
"""

from __future__ import annotations

import logging
from pathlib import Path

import joblib

from roomtype import baseline
from roomtype.labels import ABSTAIN, LABELS
from roomtype.schema import Prediction, RoomIn
from roomtype.train import MODEL_PATH

log = logging.getLogger(__name__)

#: Below this probability the model abstains rather than committing. An abstention becomes
#: UNKNOWN, which the Java side treats as "leave it for the human reviewer" instead of feeding
#: a coin-flip into an occupant-load calculation. Tune this on held-out data, not on vibes:
#: the tutorial's section on calibration explains how, and why 0.5 is rarely the right answer.
CONFIDENCE_THRESHOLD = 0.65


class Classifier:
    """Loads the model once at startup. A None model means baseline-only, which is a valid
    and expected state, not an error."""

    def __init__(self, model_path: Path | None = None) -> None:
        self._model = None
        self._featurise = None
        path = model_path or MODEL_PATH
        if not path.exists():
            log.warning("no model at %s; serving the keyword baseline", path)
            return
        try:
            # Imported lazily: features.featurise raises NotImplementedError until it is
            # written, and an unwritten feature extractor must not stop the service booting
            # on the baseline path.
            from roomtype.features import featurise

            bundle = joblib.load(path)
            self._model = bundle["model"]
            self._featurise = featurise
            log.info("loaded model from %s", path)
        except Exception:
            # A corrupt or stale artifact degrades to the baseline rather than taking the
            # service down. It is logged at exception level so it cannot pass unnoticed.
            log.exception("failed to load model at %s; serving the keyword baseline", path)
            self._model = None

    @property
    def has_model(self) -> bool:
        return self._model is not None

    def predict(self, room: RoomIn) -> Prediction:
        if self._model is None or self._featurise is None:
            occupancy, confidence = baseline.classify(room)
            return Prediction(
                id=room.id, occupancy_type=occupancy, confidence=confidence, source="baseline"
            )

        vector = self._featurise(room).reshape(1, -1)
        probabilities = self._model.predict_proba(vector)[0]
        classes = list(self._model.classes_)
        best = int(probabilities.argmax())
        occupancy = str(classes[best])
        confidence = float(probabilities[best])

        if occupancy not in LABELS or confidence < CONFIDENCE_THRESHOLD:
            # Abstain. Reports the confidence it actually had, not zero: the reviewer on the
            # Java side may want to see "it was 0.61 sure it was CA" even when we refuse to
            # act on that.
            return Prediction(
                id=room.id, occupancy_type=ABSTAIN, confidence=confidence, source="model"
            )

        return Prediction(
            id=room.id, occupancy_type=occupancy, confidence=confidence, source="model"
        )
