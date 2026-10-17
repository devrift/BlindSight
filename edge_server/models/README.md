# BlindSight ONNX Models

Place your quantized YOLOv8n ONNX model in this directory:
`yolov8n.onnx` (or `yolov8n_int8.onnx`).

The weights are not committed — `*.onnx` is in `.gitignore` — so a fresh clone
has no model and `/health` will report `vision_ready: false` until you export
one. That is the correct, honest state: the gateway must never claim a working
vision pipeline it cannot run.

## Quick Export / Download Command (1-line)

```bash
python3 -c "from ultralytics import YOLO; YOLO('yolov8n.pt').export(format='onnx', imgsz=320, simplify=True)" && mv yolov8n.onnx edge_server/models/
```

`ultralytics` is listed in `edge_server/requirements.txt`. Export at `imgsz=320`
to match the latency target in `vision_ai.py`; the loader also honours a model
exported at 640 but it will be slower on the Pi.

## Overriding the location

Set `BLINDSIGHT_MODEL_PATH=/path/to/yolov8n.onnx` in `edge_server/.env` if you
keep weights elsewhere (e.g. on an SSD rather than the SD card).

## Verifying

```bash
python3 edge_server/vision_ai.py --verbose <any_jpeg>
```

On success it prints exactly one navigation sentence on stdout. On any failure
it prints the reason on stderr and exits non-zero — see the safety contract at
the top of `vision_ai.py` and `tests/test_vision_fail_closed.py`.
