#!/usr/bin/env python3
"""BlindSight Local Edge Vision AI Engine.

Target: Raspberry Pi 3B+ (ARMv8, 1GB RAM)
Inference Engine: YOLOv8n ONNX via onnxruntime (CPU) or cv2.dnn
Footprint: <150MB RAM, execution <1.5s
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

LABEL_REPLACEMENTS = {
    "dining table": "table",
    "potted plant": "plant",
    "cell phone": "phone",
    "teddy bear": "toy",
    "hair drier": "hair dryer",
}

CONF_THRESHOLD = 0.35
IOU_THRESHOLD = 0.45


@dataclass
class Detection:
    label: str
    confidence: float
    box: tuple[float, float, float, float]
    cx_norm: float
    norm_h: float
    sector: str
    proximity: str


def get_model_path() -> Path | None:
    env_override = os.getenv("BLINDSIGHT_MODEL_PATH")
    if env_override:
        p = Path(env_override)
        return p if p.is_file() else None

    server_dir = Path(__file__).resolve().parent
    root_dir = server_dir.parent
    candidates = [
        str(server_dir / "models" / "yolov8n.onnx"),
        str(server_dir / "models" / "yolov8n_int8.onnx"),
        str(root_dir / "edge_server" / "models" / "yolov8n.onnx"),
        "/home/pi/BlindSight/edge_server/models/yolov8n.onnx",
        "edge_server/models/yolov8n.onnx",
        "models/yolov8n.onnx",
        "yolov8n.onnx",
    ]
    for c in candidates:
        if c and Path(c).is_file():
            return Path(c)
    return None


def nms(boxes: list[list[float]], scores: list[float], iou_threshold: float = 0.45) -> list[int]:
    import numpy as np
    if not boxes:
        return []

    b = np.array(boxes, dtype=np.float32)
    s = np.array(scores, dtype=np.float32)

    x1, y1, x2, y2 = b[:, 0], b[:, 1], b[:, 2], b[:, 3]
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


def run_inference(image_path: Path, model_path: Path, stats: dict) -> list[Detection]:
    from PIL import Image
    import numpy as np

    t0 = time.time()
    try:
        import onnxruntime as ort
        sess_opts = ort.SessionOptions()
        sess_opts.intra_op_num_threads = 2
        sess_opts.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
        sess_opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        session = ort.InferenceSession(str(model_path), sess_opts, providers=["CPUExecutionProvider"])
        backend = "onnxruntime"
    except Exception:
        import cv2
        net = cv2.dnn.readNetFromONNX(str(model_path))
        net.setPreferableBackend(cv2.dnn.DNN_BACKEND_OPENCV)
        net.setPreferableTarget(cv2.dnn.DNN_TARGET_CPU)
        backend = "cv2.dnn"

    stats["load_s"] = time.time() - t0
    stats["backend"] = backend

    t1 = time.time()
    img = Image.open(image_path).convert("RGB")
    orig_w, orig_h = img.size
    stats["image"] = f"{orig_w}x{orig_h}"

    input_size = (640, 640)
    resized = img.resize(input_size, Image.Resampling.BILINEAR)
    img_data = np.array(resized, dtype=np.float32) / 255.0
    img_data = np.transpose(img_data, (2, 0, 1))
    img_data = np.expand_dims(img_data, axis=0)
    stats["preprocess_s"] = time.time() - t1
    stats["input"] = f"{input_size[0]}x{input_size[1]}"

    t2 = time.time()
    if backend == "onnxruntime":
        input_name = session.get_inputs()[0].name
        outputs = session.run(None, {input_name: img_data})[0]
    else:
        net.setInput(img_data)
        outputs = net.forward()
    stats["inference_s"] = time.time() - t2

    t3 = time.time()
    output = np.squeeze(outputs)
    if output.shape[0] < output.shape[1]:
        output = np.transpose(output)

    boxes_list = []
    scores_list = []
    class_ids = []

    for row in output:
        classes_scores = row[4:]
        max_score = float(np.max(classes_scores))
        if max_score >= CONF_THRESHOLD:
            class_id = int(np.argmax(classes_scores))
            cx, cy, w, h = row[0:4]
            scale_x = orig_w / input_size[0]
            scale_y = orig_h / input_size[1]
            x1 = (cx - w / 2) * scale_x
            y1 = (cy - h / 2) * scale_y
            x2 = (cx + w / 2) * scale_x
            y2 = (cy + h / 2) * scale_y
            boxes_list.append([x1, y1, x2, y2])
            scores_list.append(max_score)
            class_ids.append(class_id)

    keep_indices = nms(boxes_list, scores_list, IOU_THRESHOLD)

    detections = []
    for idx in keep_indices:
        b = boxes_list[idx]
        cid = class_ids[idx]
        raw_label = COCO_CLASSES[cid] if cid < len(COCO_CLASSES) else f"object_{cid}"
        label = LABEL_REPLACEMENTS.get(raw_label, raw_label)

        cx_norm = ((b[0] + b[2]) / 2.0) / orig_w
        norm_h = (b[3] - b[1]) / orig_h

        sector = "left" if cx_norm < 0.35 else ("right" if cx_norm > 0.65 else "center")
        proximity = "immediate" if norm_h > 0.55 else ("near" if norm_h > 0.30 else "distant")

        detections.append(Detection(
            label=label,
            confidence=scores_list[idx],
            box=(b[0], b[1], b[2], b[3]),
            cx_norm=cx_norm,
            norm_h=norm_h,
            sector=sector,
            proximity=proximity,
        ))

    stats["postprocess_s"] = time.time() - t3
    stats["total_s"] = stats["load_s"] + stats["preprocess_s"] + stats["inference_s"] + stats["postprocess_s"]
    return detections


def generate_sentence(detections: list[Detection], orig_w: float = 320.0, orig_h: float = 240.0) -> str:
    """Produces clear, safety-oriented navigation instructions."""
    if not detections:
        return "Path ahead appears clear."

    center_hazards = [d for d in detections if d.sector == "center"]
    left_hazards = [d for d in detections if d.sector == "left"]
    right_hazards = [d for d in detections if d.sector == "right"]

    center_hazards.sort(key=lambda d: d.norm_h, reverse=True)
    left_hazards.sort(key=lambda d: d.norm_h, reverse=True)
    right_hazards.sort(key=lambda d: d.norm_h, reverse=True)

    if center_hazards:
        target = center_hazards[0]
        prefix = f"Warning: {target.label.capitalize()} close ahead." if target.norm_h > 0.55 else f"Caution: {target.label.capitalize()} ahead."

        span = orig_w if orig_w > 0 else 1.0
        left_density = sum(d.norm_h * ((d.box[2] - d.box[0]) / span) for d in left_hazards)
        right_density = sum(d.norm_h * ((d.box[2] - d.box[0]) / span) for d in right_hazards)

        if not left_hazards and not right_hazards:
            return f"{prefix} Path clear on your left and right."
        elif not right_hazards or right_density < left_density:
            return f"{prefix} Path clear on your right."
        elif not left_hazards or left_density < right_density:
            return f"{prefix} Path clear on your left."
        else:
            return f"{prefix} Caution, all paths obstructed."

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

    if left_hazards and right_hazards:
        l_target = left_hazards[0]
        r_target = right_hazards[0]
        prefix = "Warning: " if max(l_target.norm_h, r_target.norm_h) > 0.55 else "Caution: "
        return f"{prefix}{l_target.label.capitalize()} on left and {r_target.label} on right. Path ahead is clear."

    return "Path ahead appears clear."


def main() -> int:
    parser = argparse.ArgumentParser(description="Analyze an image frame for obstacles.")
    parser.add_argument("image", type=Path, help="JPEG/PNG image file to analyze")
    args = parser.parse_args()

    if not args.image.is_file():
        print(f"Error: image not found: {args.image}", file=sys.stderr)
        return 1

    model_path = get_model_path()
    if model_path is None:
        print("Error: YOLOv8n ONNX model not found", file=sys.stderr)
        return 1

    stats: dict = {}
    try:
        detections = run_inference(args.image, model_path, stats)
        sentence = generate_sentence(detections)
        print(sentence)
        return 0
    except Exception as e:
        print(f"Error during inference: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
