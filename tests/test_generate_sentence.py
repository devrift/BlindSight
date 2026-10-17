"""Sentence generation rules for the spoken navigation output (audit D1).

These pin the behaviour of generate_sentence() so the merge-conflict resolution
(keep HEAD's density-based escape-path choice and Warning/Caution prefixes)
cannot silently regress.
"""
from __future__ import annotations

import vision_ai
from vision_ai import Detection


def det(label="chair", norm_h=0.6, sector="center", cx=0.5, w=100.0, conf=0.8):
    x1 = cx * 320.0 - w / 2.0
    return Detection(
        label=label,
        confidence=conf,
        box=(x1, 10.0, x1 + w, 10.0 + norm_h * 240.0),
        cx_norm=cx,
        norm_h=norm_h,
        sector=sector,
        proximity="immediate" if norm_h > 0.55 else ("near" if norm_h > 0.30 else "distant"),
    )


def gen(dets, orig_w=320.0, orig_h=240.0):
    return vision_ai.generate_sentence(dets, orig_w=orig_w, orig_h=orig_h)


def test_no_detections_is_clear():
    assert gen([]) == "Path ahead appears clear."


def test_only_distant_objects_is_clear():
    assert gen([det(norm_h=0.20)]) == "Path ahead appears clear."


def test_centre_immediate_warns_and_offers_both_sides():
    assert gen([det(norm_h=0.7)]) == "Warning: Chair close ahead. Path clear on your left and right."


def test_centre_near_uses_caution():
    assert gen([det(norm_h=0.4)]) == "Caution: Chair ahead. Path clear on your left and right."


def test_centre_hazard_with_left_flank_offers_right():
    out = gen([det(norm_h=0.7), det(sector="left", cx=0.1, norm_h=0.6)])
    assert out == "Warning: Chair close ahead. Path clear on your right."


def test_centre_hazard_with_right_flank_offers_left():
    out = gen([det(norm_h=0.7), det(sector="right", cx=0.9, norm_h=0.6)])
    assert out == "Warning: Chair close ahead. Path clear on your left."


def test_escape_path_prefers_less_dense_flank():
    # Left flank is denser (taller x wider), so the right is the escape path.
    left = det(sector="left", cx=0.1, norm_h=0.8, w=160)
    right = det(sector="right", cx=0.9, norm_h=0.4, w=60)
    out = gen([det(norm_h=0.7), left, right])
    assert out == "Warning: Chair close ahead. Path clear on your right."


def test_equal_flank_density_reports_all_obstructed():
    left = det(sector="left", cx=0.1, norm_h=0.5, w=100)
    right = det(sector="right", cx=0.9, norm_h=0.5, w=100)
    out = gen([det(norm_h=0.7), left, right])
    assert out == "Warning: Chair close ahead. Caution, all paths obstructed."


def test_left_flank_only():
    out = gen([det(sector="left", cx=0.1, norm_h=0.4)])
    assert out == "Caution: Chair on your left. Path ahead is clear."


def test_left_flank_immediate_uses_warning():
    out = gen([det(sector="left", cx=0.1, norm_h=0.8)])
    assert out == "Warning: Chair close on your left. Path ahead is clear."


def test_right_flank_only():
    out = gen([det(sector="right", cx=0.9, norm_h=0.4)])
    assert out == "Caution: Chair on your right. Path ahead is clear."


def test_both_flanks_no_centre():
    out = gen([det(sector="left", cx=0.1, norm_h=0.4), det(label="table", sector="right", cx=0.9, norm_h=0.4)])
    assert out == "Caution: Chair on left and table on right. Path ahead is clear."


def test_label_replacements_have_no_self_mappings():
    """Audit D9: 'traffic light': 'traffic light' was a dead entry that hid intent."""
    for source, target in vision_ai.LABEL_REPLACEMENTS.items():
        assert source != target, f"LABEL_REPLACEMENTS[{source!r}] maps to itself"


def test_zero_width_image_does_not_divide_by_zero():
    out = gen([det(sector="left", cx=0.1, norm_h=0.6), det(norm_h=0.7)], orig_w=0.0)
    assert out.startswith(("Warning:", "Caution"))
