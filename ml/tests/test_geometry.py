import math

from roomtype import geometry
from roomtype.schema import Point


def square(side: float) -> list[Point]:
    return [Point(x=0, y=0), Point(x=side, y=0), Point(x=side, y=side), Point(x=0, y=side)]


def test_area_of_a_square():
    assert geometry.area(square(3)) == 9.0


def test_area_is_winding_order_independent():
    assert geometry.area(list(reversed(square(3)))) == 9.0


def test_degenerate_polygon_has_no_area_and_does_not_divide_by_zero():
    assert geometry.area([Point(x=0, y=0), Point(x=1, y=1)]) == 0.0
    assert geometry.aspect_ratio([]) == 1.0
    assert geometry.compactness([]) == 0.0


def test_corridor_is_long_and_thin():
    corridor = [Point(x=0, y=0), Point(x=20, y=0), Point(x=20, y=2), Point(x=0, y=2)]
    assert geometry.aspect_ratio(corridor) == 10.0
    assert geometry.compactness(corridor) < geometry.compactness(square(3))


def test_square_compactness_is_the_known_constant():
    assert math.isclose(geometry.compactness(square(5)), math.pi / 4, rel_tol=1e-9)
