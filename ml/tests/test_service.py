from fastapi.testclient import TestClient

from roomtype.service import app

client = TestClient(app)


def test_health_reports_baseline_mode_honestly():
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["model_loaded"] is False  # no artifact committed; baseline is the healthy state


def test_classify_falls_back_to_the_baseline_and_says_so():
    response = client.post(
        "/classify",
        json={"rooms": [{"id": "room-1", "label": "Boardroom", "polygon": []}]},
    )
    assert response.status_code == 200
    prediction = response.json()["predictions"][0]
    assert prediction["id"] == "room-1"
    assert prediction["occupancy_type"] == "WB"
    assert prediction["source"] == "baseline"


def test_an_unrecognised_room_abstains_instead_of_being_labelled():
    # The Java client drops UNKNOWN, so this room arrives at the reviewer with no type, which is
    # the correct outcome: nothing about "Zorb Chamber" tells us its occupant density.
    response = client.post(
        "/classify",
        json={"rooms": [{"id": "room-9", "label": "Zorb Chamber", "polygon": []}]},
    )
    prediction = response.json()["predictions"][0]
    assert prediction["occupancy_type"] == "UNKNOWN"
    assert prediction["confidence"] == 0.0
