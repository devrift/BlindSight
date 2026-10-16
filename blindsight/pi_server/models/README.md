# BlindSight ONNX Models

Place your quantized YOLOv8n ONNX model in this directory:
`yolov8n.onnx`

## Quick Export / Download Command (1-line):
```bash
python3 -c "from ultralytics import YOLO; YOLO('yolov8n.pt').export(format='onnx', imgsz=320, simplify=True)" && mv yolov8n.onnx blindsight/pi_server/models/
```
