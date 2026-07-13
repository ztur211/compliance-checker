"""Polygon primitives.

These are plumbing, not machine learning: they are given to you so that choosing *which*
of these numbers matter (that is the modelling decision) stays your job. See features.py.
"""

from __future__ import annotations

import math
from collections.abc import Sequence

from roomtype.schema import Point


def area(polygon: Sequence[Point]) -> float:
    """Shoelace formula. Returns a non-negative area regardless of winding order."""
    if len(polygon) < 3:
        return 0.0
    total = 0.0
    for i, a in enumerate(polygon):
        b = polygon[(i + 1) % len(polygon)]
        total += a.x * b.y - b.x * a.y
    return abs(total) / 2.0


def perimeter(polygon: Sequence[Point]) -> float:
    if len(polygon) < 2:
        return 0.0
    total = 0.0
    for i, a in enumerate(polygon):
        b = polygon[(i + 1) % len(polygon)]
        total += math.dist((a.x, a.y), (b.x, b.y))
    return total


def bounding_box(polygon: Sequence[Point]) -> tuple[float, float]:
    """(width, height) of the axis-aligned bounding box."""
    if not polygon:
        return (0.0, 0.0)
    xs = [p.x for p in polygon]
    ys = [p.y for p in polygon]
    return (max(xs) - min(xs), max(ys) - min(ys))


def aspect_ratio(polygon: Sequence[Point]) -> float:
    """Long side / short side of the bounding box. 1.0 is square, large is corridor-like.

    Returns 1.0 for a degenerate polygon rather than dividing by zero. Think about whether
    that default is a lie your model will learn from.
    """
    w, h = bounding_box(polygon)
    lo, hi = min(w, h), max(w, h)
    if lo <= 0.0:
        return 1.0
    return hi / lo


def compactness(polygon: Sequence[Point]) -> float:
    """Isoperimetric quotient: 4*pi*area / perimeter^2. 1.0 is a circle, ~0.79 a square,
    and it falls toward 0 for long thin or ragged shapes."""
    p = perimeter(polygon)
    if p <= 0.0:
        return 0.0
    return (4.0 * math.pi * area(polygon)) / (p * p)
