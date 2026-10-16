#!/usr/bin/env python3
"""BlindSight Local Edge Vision AI Engine.

Target: Raspberry Pi 3B+ (ARMv8, 1GB RAM)
Inference Engine: YOLOv8n ONNX via onnxruntime (CPU) or cv2.dnn
Footprint: <150MB RAM, execution <1.5s

Usage:
    python3 vision_ai.py [--verbose] <image_path>

Success prints exactly one navigation message on stdout and exits 0.
Any failure (no model, unreadable image, backend error) exits non-zero with
the reason on stderr, so server.py speaks its safe fallback instead of
announcing a clear path. --verbose adds timing and detections on stderr.
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from dataclasses import dataclass
from pathlib import Path

# Common COCO class names (80 classes)
COCO_CLASSES = [
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
    "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
    "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
    "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
    "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
    "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
    "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
    "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote",
    "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book",
    "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
]

# Simplify select object names for voice synthesis
LABEL_REPLACEMENTS = {
    "dining table": "table",
    "potted plant": "plant",
    "traffic light": "traffic light",
}

CONF_THRESHOLD = 0.35
IOU_THRESHOLD = 0.45


@dataclass
class Detection:
    label: str
    confidence: float
    box: tuple[float, float, float, float]  # x1, y1, x2, y2 (pixel coords)
    cx_norm: float                         # normalized center x [0.0 - 1.0]
    norm_h: float                          # normalized height [0.0 - 1.0]
    sector: str                            # "left", "center", "right"
    proximity: str                         # "immediate", "near", "distant"


def get_model_path() -> Path | None:
    """Finds available YOLOv8n ONNX model weights in standard search paths."""
<<<<<<< HEAD:edge_server/vision_ai.py
    server_dir = Path(__file__).resolve().parent
    root_dir = server_dir.parent
    candidates = [
        os.getenv("BLINDSIGHT_MODEL_PATH"),
        str(server_dir / "models" / "yolov8n.onnx"),
        str(server_dir / "models" / "yolov8n_int8.onnx"),
        str(root_dir / "edge_server" / "models" / "yolov8n.onnx"),
        "/home/pi/BlindSight/edge_server/models/yolov8n.onnx",
        "/home/pi/blindsight/pi_server/models/yolov8n.onnx",
        "edge_server/models/yolov8n.onnx",
=======
    candidates = [
        os.getenv("BLINDSIGHT_MODEL_PATH"),
        str(Path(__file__).resolve().parent / "models" / "yolov8n.onnx"),
        str(Path(__file__).resolve().parent / "models" / "yolov8n_int8.onnx"),
        "/home/pi/blindsight/pi_server/models/yolov8n.onnx",
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py
        "models/yolov8n.onnx",
        "yolov8n.onnx",
    ]
    for c in candidates:
        if c and Path(c).is_file():
            return Path(c)
    return None


def nms(boxes: list[list[float]], scores: list[float], iou_threshold: float = 0.45) -> list[int]:
    """Pure Python/NumPy Non-Maximum Suppression."""
    import numpy as np

    if not boxes:
        return []

    b = np.array(boxes, dtype=np.float32)
    s = np.array(scores, dtype=np.float32)

    x1 = b[:, 0]
    y1 = b[:, 1]
    x2 = b[:, 2]
    y2 = b[:, 3]
    areas = np.maximum(0.0, x2 - x1) * np.maximum(0.0, y2 - y1)
    order = s.argsort()[::-1]

    keep: list[int] = []
    while order.size > 0:
        i = order[0]
        keep.append(int(i))
        if order.size == 1:
            break

        xx1 = np.maximum(x1[i], x1[order[1:]])
        yy1 = np.maximum(y1[i], y1[order[1:]])
        xx2 = np.minimum(x2[i], x2[order[1:]])
        yy2 = np.minimum(y2[i], y2[order[1:]])

        w = np.maximum(0.0, xx2 - xx1)
        h = np.maximum(0.0, yy2 - yy1)
        inter = w * h
        union = areas[i] + areas[order[1:]] - inter
        iou = np.where(union > 0, inter / union, 0.0)

        inds = np.where(iou <= iou_threshold)[0]
        order = order[inds + 1]

    return keep


def run_inference(image_path: Path, model_path: Path, stats: dict | None = None) -> list[Detection]:
    """Runs YOLOv8n ONNX inference via onnxruntime or cv2.dnn fallback.

    Raises if no backend can run the model; an empty list means the model ran
    and found nothing. Backend, sizes and stage timings are recorded in `stats`.
    """
    import numpy as np
    from PIL import Image

    if stats is None:
        stats = {}
    t_start = time.perf_counter()

    # Default model target dimension: 320 for speed on Pi, or 640
    target_w, target_h = 320, 320

    # Try onnxruntime first
    session = None
    cv2_net = None
    try:
        import onnxruntime as ort

        sess_opts = ort.SessionOptions()
        sess_opts.intra_op_num_threads = 2
        sess_opts.inter_op_num_threads = 1
        sess_opts.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
<<<<<<< HEAD:edge_server/vision_ai.py
        sess_opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC
=======
        sess_opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py

        session = ort.InferenceSession(str(model_path), sess_opts, providers=["CPUExecutionProvider"])
        input_shape = session.get_inputs()[0].shape
        if len(input_shape) == 4:
            if isinstance(input_shape[2], int) and input_shape[2] > 0:
                target_h = input_shape[2]
            if isinstance(input_shape[3], int) and input_shape[3] > 0:
                target_w = input_shape[3]
        stats["backend"] = f"onnxruntime {ort.__version__}"
    except Exception as ort_error:
        try:
            import cv2
            cv2_net = cv2.dnn.readNetFromONNX(str(model_path))
        except Exception as cv2_error:
            # Returning no detections here would be spoken as "path clear"
            raise RuntimeError(
                f"no backend can load {model_path} (onnxruntime: {ort_error}; cv2.dnn: {cv2_error})"
            ) from None
        stats["backend"] = f"cv2.dnn {cv2.__version__}"
    t_loaded = time.perf_counter()

    # Load image and note original dimensions
    with Image.open(image_path) as src:
        pil_img = src.convert("RGB")
    orig_w, orig_h = pil_img.size
    stats["input"] = f"{target_w}x{target_h}"
    stats["image"] = f"{orig_w}x{orig_h}"

    # Aspect-ratio preserving letterbox resize
    scale = min(target_w / orig_w, target_h / orig_h)
    new_w = int(round(orig_w * scale))
    new_h = int(round(orig_h * scale))
    resized = pil_img.resize((new_w, new_h), Image.Resampling.BILINEAR)

    pad_left = (target_w - new_w) // 2
    pad_top = (target_h - new_h) // 2

    canvas = Image.new("RGB", (target_w, target_h), (114, 114, 114))
    canvas.paste(resized, (pad_left, pad_top))

    # Normalize to float32 [0.0 - 1.0], shape [1, 3, target_h, target_w]
    img_arr = np.asarray(canvas, dtype=np.float32) / 255.0
    input_tensor = np.ascontiguousarray(img_arr.transpose(2, 0, 1)[np.newaxis])
    t_prepped = time.perf_counter()

    # Execute model
    if session is not None:
        input_name = session.get_inputs()[0].name
        output = session.run(None, {input_name: input_tensor})[0]  # shape: (1, 84, N)
    else:
        cv2_net.setInput(input_tensor)
        output = cv2_net.forward()
    t_inferred = time.perf_counter()

    # YOLOv8 output post-processing: transpose to (N, 84)
    # columns: [cx, cy, w, h, class_0_prob, ... class_79_prob]
    preds = output[0].T if output.ndim == 3 else output

    # Vectorised over all N candidates (2100 at 320x320) instead of a per-row loop
    class_scores = preds[:, 4:]
    class_ids = class_scores.argmax(axis=1)
    scores = class_scores[np.arange(len(class_ids)), class_ids]
    confident = scores >= CONF_THRESHOLD
    preds, class_ids, scores = preds[confident], class_ids[confident], scores[confident]

    # Map from letterbox back to original image coordinates, then clip
    cx, cy, bw, bh = preds[:, 0], preds[:, 1], preds[:, 2], preds[:, 3]
    boxes = np.stack([
        np.clip((cx - bw / 2.0 - pad_left) / scale, 0.0, orig_w),
        np.clip((cy - bh / 2.0 - pad_top) / scale, 0.0, orig_h),
        np.clip((cx + bw / 2.0 - pad_left) / scale, 0.0, orig_w),
        np.clip((cy + bh / 2.0 - pad_top) / scale, 0.0, orig_h),
    ], axis=1)
    valid = (boxes[:, 2] > boxes[:, 0]) & (boxes[:, 3] > boxes[:, 1])
    boxes, scores, class_ids = boxes[valid], scores[valid], class_ids[valid]

    # NMS
    keep_indices = nms(boxes.tolist(), scores.tolist(), iou_threshold=IOU_THRESHOLD)

    detections: list[Detection] = []
    for idx in keep_indices:
        x1, y1, x2, y2 = (float(v) for v in boxes[idx])
        class_id = int(class_ids[idx])

        label = COCO_CLASSES[class_id] if class_id < len(COCO_CLASSES) else "object"
        label = LABEL_REPLACEMENTS.get(label, label)

        # Spatial mapping
        cx_norm = ((x1 + x2) / 2.0) / orig_w
        norm_h = (y2 - y1) / orig_h

        # Horizontal sector:
        # Left (x < 33%), Center (33% <= x <= 66%), Right (x > 66%)
        if cx_norm < 0.33:
            sector = "left"
        elif cx_norm <= 0.66:
            sector = "center"
        else:
            sector = "right"

        # Proximity estimation via normalized height:
        # > 0.55 = Immediate hazard, > 0.30 = Near, <= 0.30 = Distant
        if norm_h > 0.55:
            proximity = "immediate"
        elif norm_h > 0.30:
            proximity = "near"
        else:
            proximity = "distant"

        detections.append(Detection(
            label=label,
            confidence=float(scores[idx]),
            box=(x1, y1, x2, y2),
            cx_norm=cx_norm,
            norm_h=norm_h,
            sector=sector,
            proximity=proximity,
        ))

    t_done = time.perf_counter()
    stats.update(
        load_s=t_loaded - t_start,
        preprocess_s=t_prepped - t_loaded,
        inference_s=t_inferred - t_prepped,
        postprocess_s=t_done - t_inferred,
        total_s=t_done - t_start,
    )
    return detections


<<<<<<< HEAD:edge_server/vision_ai.py
def generate_sentence(detections: list[Detection], orig_w: float = 320.0, orig_h: float = 240.0) -> str:
=======
def generate_sentence(detections: list[Detection]) -> str:
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py
    """Generates exactly ONE clean sentence prioritizing central and close hazards."""
    if not detections:
        return "Path ahead appears clear."

    # Filter for navigation-relevant obstacles (>0.30 height)
    close_hazards = [d for d in detections if d.norm_h > 0.30]

<<<<<<< HEAD:edge_server/vision_ai.py
    # <= 0.30: Distant object (Ignore if central path is clear)
=======
    # If only distant objects are in view, path ahead remains navigable
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py
    if not close_hazards:
        return "Path ahead appears clear."

    center_hazards = [d for d in close_hazards if d.sector == "center"]
    left_hazards = [d for d in close_hazards if d.sector == "left"]
    right_hazards = [d for d in close_hazards if d.sector == "right"]

    center_hazards.sort(key=lambda d: d.norm_h, reverse=True)
    left_hazards.sort(key=lambda d: d.norm_h, reverse=True)
    right_hazards.sort(key=lambda d: d.norm_h, reverse=True)

    # 1. Central hazard (highest priority)
    if center_hazards:
        target = center_hazards[0]
<<<<<<< HEAD:edge_server/vision_ai.py
        # > 0.55: Immediate hazard (Prepends "Warning: ...")
        # > 0.30: Near hazard (Prepends "Caution: ...")
        if target.norm_h > 0.55:
            prefix = f"Warning: {target.label.capitalize()} close ahead."
        else:
            prefix = f"Caution: {target.label.capitalize()} ahead."

        # Dynamic escape path evaluation: assess lateral bounding box density
        left_density = sum(d.norm_h * ((d.box[2] - d.box[0]) / orig_w) for d in left_hazards)
        right_density = sum(d.norm_h * ((d.box[2] - d.box[0]) / orig_w) for d in right_hazards)

        if not left_hazards and not right_hazards:
            return f"{prefix} Path clear on your left and right."
        elif not right_hazards or right_density < left_density:
            return f"{prefix} Path clear on your right."
        elif not left_hazards or left_density < right_density:
=======
        proximity_str = "close ahead" if target.norm_h > 0.55 else "ahead"
        prefix = f"Warning: {target.label.capitalize()} {proximity_str}."

        left_blocked = bool(left_hazards)
        right_blocked = bool(right_hazards)

        if not left_blocked and not right_blocked:
            return f"{prefix} Path clear on your left and right."
        elif not right_blocked:
            return f"{prefix} Path clear on your right."
        elif not left_blocked:
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py
            return f"{prefix} Path clear on your left."
        else:
            return f"{prefix} Caution, all paths obstructed."

<<<<<<< HEAD:edge_server/vision_ai.py
    # 2. Side hazards when central path is clear
    if left_hazards and not right_hazards:
        target = left_hazards[0]
        prefix = "Warning: " if target.norm_h > 0.55 else "Caution: "
        proximity_str = "close on your left" if target.norm_h > 0.55 else "on your left"
        return f"{prefix}{target.label.capitalize()} {proximity_str}. Path ahead is clear."

    if right_hazards and not left_hazards:
        target = right_hazards[0]
        prefix = "Warning: " if target.norm_h > 0.55 else "Caution: "
        proximity_str = "close on your right" if target.norm_h > 0.55 else "on your right"
        return f"{prefix}{target.label.capitalize()} {proximity_str}. Path ahead is clear."
=======
    # 2. Side hazards when center is clear
    if left_hazards and not right_hazards:
        target = left_hazards[0]
        proximity_str = "close on your left" if target.norm_h > 0.55 else "on your left"
        return f"Caution: {target.label.capitalize()} {proximity_str}. Path ahead is clear."

    if right_hazards and not left_hazards:
        target = right_hazards[0]
        proximity_str = "close on your right" if target.norm_h > 0.55 else "on your right"
        return f"Caution: {target.label.capitalize()} {proximity_str}. Path ahead is clear."
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py

    if left_hazards and right_hazards:
        l_target = left_hazards[0]
        r_target = right_hazards[0]
<<<<<<< HEAD:edge_server/vision_ai.py
        prefix = "Warning: " if max(l_target.norm_h, r_target.norm_h) > 0.55 else "Caution: "
        return f"{prefix}{l_target.label.capitalize()} on left and {r_target.label} on right. Path ahead is clear."
=======
        return f"Caution: {l_target.label.capitalize()} on left and {r_target.label} on right. Path ahead is clear."
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py

    return "Path ahead appears clear."


def print_report(model_path: Path, stats: dict, detections: list[Detection]) -> None:
    """Writes backend, stage timings and per-object zones to stderr."""
    err = sys.stderr
    print(
        f"vision_ai: model {model_path.name} | {stats['backend']} | "
        f"input {stats['input']} | image {stats['image']}",
        file=err,
    )
    print(
        "vision_ai: load {load_s:.3f}s | preprocess {preprocess_s:.3f}s | "
        "inference {inference_s:.3f}s | postprocess {postprocess_s:.3f}s | total {total_s:.3f}s".format(**stats),
        file=err,
    )
    print(f"vision_ai: {len(detections)} detection(s)", file=err)
    for d in detections:
        x1, y1, x2, y2 = d.box
        print(
            f"vision_ai:   {d.label:<14} {d.confidence:.2f}  {d.sector + '/' + d.proximity:<17} "
            f"h={d.norm_h:.2f}  box=({x1:.0f},{y1:.0f},{x2:.0f},{y2:.0f})",
            file=err,
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Print one navigation sentence for a camera frame.")
    parser.add_argument("image", type=Path, help="JPEG/PNG frame to analyse")
    parser.add_argument("-v", "--verbose", action="store_true", help="timing and detections on stderr")
    args = parser.parse_args(argv)

    if not args.image.is_file():
<<<<<<< HEAD:edge_server/vision_ai.py
        print("Path ahead appears clear.")
        return 0

    model_path = get_model_path()
    if model_path is None:
        print("Path ahead appears clear.")
        return 0
=======
        print(f"vision_ai: image not found: {args.image}", file=sys.stderr)
        return 1

    model_path = get_model_path()
    if model_path is None:
        print("vision_ai: yolov8n.onnx not found (see models/README.md or set BLINDSIGHT_MODEL_PATH)", file=sys.stderr)
        return 1
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py

    stats: dict = {}
    try:
        detections = run_inference(args.image, model_path, stats)
<<<<<<< HEAD:edge_server/vision_ai.py
        orig_w = float(stats.get("image", "320x240").split("x")[0])
        orig_h = float(stats.get("image", "320x240").split("x")[1])
        sentence = generate_sentence(detections, orig_w=orig_w, orig_h=orig_h)
    except Exception as error:
        print(f"vision_ai: fallback triggered: {type(error).__name__}: {error}", file=sys.stderr)
        print("Path ahead appears clear.")
        return 0
=======
        sentence = generate_sentence(detections)
    except Exception as error:
        print(f"vision_ai: inference failed: {type(error).__name__}: {error}", file=sys.stderr)
        return 1
>>>>>>> 2b0cbbc5c2a7bee427d9c9f3e473728a6d668c23:blindsight/pi_server/vision_ai.py

    print(sentence)
    if args.verbose:
        print_report(model_path, stats, detections)
    return 0


if __name__ == "__main__":
    sys.exit(main())
