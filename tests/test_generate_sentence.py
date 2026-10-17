from __future__ import annotations

import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT_DIR / "edge_server"))

from vision_ai import Detection, generate_sentence


def test_no_detections():
    assert generate_sentence([]) == "Path ahead appears clear."


def test_distant_only_detections():
    # norm_h <= 0.30 should be treated as distant and ignored if center is open
    d = Detection(
        label="chair",
        confidence=0.8,
        box=(100, 100, 150, 150),
        cx_norm=0.5,
        norm_h=0.25,
        sector="center",
        proximity="distant",
    )
    assert generate_sentence([d]) == "Path ahead appears clear."


def test_central_immediate_hazard():
    d = Detection(
        label="person",
        confidence=0.9,
        box=(100, 50, 200, 220),
        cx_norm=0.5,
        norm_h=0.70,
        sector="center",
        proximity="immediate",
    )
    sentence = generate_sentence([d], orig_w=320, orig_h=240)
    assert sentence.startswith("Warning: Person close ahead.")
    assert "Path clear on your left and right." in sentence


def test_central_near_hazard():
    d = Detection(
        label="chair",
        confidence=0.85,
        box=(100, 80, 200, 180),
        cx_norm=0.5,
        norm_h=0.42,
        sector="center",
        proximity="near",
    )
    sentence = generate_sentence([d], orig_w=320, orig_h=240)
    assert sentence.startswith("Caution: Chair ahead.")


def test_lateral_avoidance_direction():
    # Center hazard plus a hazard on the left -> should tell user path clear on right
    center = Detection(
        label="chair",
        confidence=0.85,
        box=(120, 80, 200, 220),
        cx_norm=0.5,
        norm_h=0.60,
        sector="center",
        proximity="immediate",
    )
    left = Detection(
        label="table",
        confidence=0.80,
        box=(10, 80, 80, 200),
        cx_norm=0.15,
        norm_h=0.50,
        sector="left",
        proximity="near",
    )
    sentence = generate_sentence([center, left], orig_w=320, orig_h=240)
    assert "Path clear on your right." in sentence


def test_side_only_hazards():
    # Left hazard only
    left = Detection(
        label="bicycle",
        confidence=0.85,
        box=(10, 80, 80, 220),
        cx_norm=0.15,
        norm_h=0.60,
        sector="left",
        proximity="immediate",
    )
    sentence = generate_sentence([left], orig_w=320, orig_h=240)
    assert sentence.startswith("Warning: Bicycle close on your left. Path ahead is clear.")
