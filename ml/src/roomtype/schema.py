"""Wire contract between the Java import pipeline and this service.

Keep this in sync with `app/src/main/java/nz/compliance/app/imports/RoomTypeClassifier.java`
and its DTOs. There is a contract test on the Java side that posts a fixture against these
shapes; if you rename a field here, that test is what should fail.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class Point(BaseModel):
    x: float
    y: float


class RoomIn(BaseModel):
    """One room to classify.

    The geometry is in METRES, not pixels. The Java side has already applied the scale
    from the import, so this service never has to know about images. If a plan had no
    resolvable scale, the Java side does not call us at all: a classifier fed pixel
    coordinates it believes are metres would produce garbage areas and therefore garbage
    predictions, which is the worst failure mode available to us.
    """

    id: str
    label: str = Field(default="", description="Room name as printed on the plan, may be empty")
    polygon: list[Point]
    door_count: int = Field(default=0, ge=0)
    connected_room_count: int = Field(default=0, ge=0, description="Graph degree, excluding exits")
    has_exit_door: bool = False


class ClassifyRequest(BaseModel):
    rooms: list[RoomIn]


class Prediction(BaseModel):
    id: str
    occupancy_type: str
    confidence: float = Field(ge=0.0, le=1.0)
    #: What produced this: "model" or "baseline". The import pipeline surfaces this to the
    #: reviewer, because "a keyword rule guessed this" and "a trained model predicted this"
    #: warrant different levels of trust from the human approving the plan.
    source: str


class ClassifyResponse(BaseModel):
    predictions: list[Prediction]
