from roomtype import baseline
from roomtype.labels import CA, UNKNOWN, WB
from roomtype.schema import RoomIn


def room(label: str) -> RoomIn:
    return RoomIn(id="r1", label=label, polygon=[])


def test_matches_keyword_case_insensitively():
    assert baseline.classify(room("Open Office"))[0] == WB
    assert baseline.classify(room("DINING HALL"))[0] == CA


def test_unmatched_label_abstains_rather_than_guessing():
    # An unrecognised room must NOT be handed an occupancy type. A type sets occupant load, which
    # decides pass/fail, so a guess here is a fabricated safety input. UNKNOWN leaves it to a human.
    occupancy, confidence = baseline.classify(room("Zorb Chamber"))
    assert occupancy == UNKNOWN
    assert confidence == 0.0


def test_empty_label_abstains():
    assert baseline.classify(room(""))[0] == UNKNOWN
