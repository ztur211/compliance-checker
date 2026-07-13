"""The baseline: keyword matching on the room's printed label.

This is given to you fully implemented, and it has a job beyond being a fallback. It is the
number your model has to beat.

A model that scores 82% sounds good until you find out that matching a dozen keywords scores
80%, at which point you have bought two points of accuracy for a training pipeline, a model
artifact, a serving path, and a whole new way for production to break. Sometimes that trade is
right. You cannot know whether it is right here until you have measured the dumb thing, and
almost nobody measures the dumb thing. Measure the dumb thing.

It is also the honest fallback: it needs no training data, no model file, and no confidence
calibration, so the service can answer sensibly on day one and on the day the model artifact
fails to load.
"""

from __future__ import annotations

from roomtype.labels import CA, UNKNOWN, WB
from roomtype.schema import RoomIn

# Ordered most-specific first: the first hit wins, so "dining hall" must be tested before
# a generic "hall" rule would be, if you ever add one.
_KEYWORDS: tuple[tuple[str, str], ...] = (
    # crowd activity: people gather here, so m2/person is low and occupant load is high
    ("assembly", CA),
    ("auditorium", CA),
    ("hall", CA),
    ("dining", CA),
    ("cafe", CA),
    ("canteen", CA),
    ("restaurant", CA),
    ("bar", CA),
    ("lounge", CA),
    ("retail", CA),
    ("shop", CA),
    ("classroom", CA),
    ("lecture", CA),
    ("theatre", CA),
    ("gym", CA),
    # working / business
    ("office", WB),
    ("admin", WB),
    ("reception", WB),
    ("meeting", WB),
    ("boardroom", WB),
    ("workshop", WB),
    ("studio", WB),
    ("store", WB),
    ("storage", WB),
    ("plant", WB),
    ("corridor", WB),
    ("lobby", WB),
    ("stair", WB),
    ("toilet", WB),
    ("bath", WB),
    ("kitchen", WB),
)

#: A keyword hit is a rule firing, not a probability. Reporting 1.0 would be a lie, and the
#: service thresholds on this value, so the lie would have consequences. This number says
#: "better than nothing, worse than a fitted model" and nothing more.
KEYWORD_CONFIDENCE = 0.6

#: When nothing matches, we abstain. It is tempting to fall back to WB instead, since WB is the
#: engine's own default density and the answer would often be right. Do not. An abstention leaves
#: the field blank for the reviewer to fill; a WB guess writes a real occupancy type onto the plan,
#: and occupancy type sets occupant load, which decides whether the building passes. Those two
#: outcomes look identical in the data and are completely different in a fire.
#:
#: Note the abstention costs us nothing: OccupantDensity already defaults to 10 m2/person for a
#: blank type, so abstaining lands on the same density WB would have, without asserting a type
#: nobody has any evidence for.
DEFAULT = UNKNOWN
DEFAULT_CONFIDENCE = 0.0


def classify(room: RoomIn) -> tuple[str, float]:
    """Return (occupancy_type, confidence). UNKNOWN means "no opinion", not "some other type"."""
    label = (room.label or "").casefold()
    for keyword, occupancy in _KEYWORDS:
        if keyword in label:
            return occupancy, KEYWORD_CONFIDENCE
    return DEFAULT, DEFAULT_CONFIDENCE
