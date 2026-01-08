MobileFaceNet Model Required
=============================

This app requires the MobileFaceNet TFLite model to function properly.

HOW TO GET THE MODEL:
1. Go to: https://github.com/deepinsight/insightface/releases/tag/v0.7
2. Download either buffalo_s.zip (122 MB) or buffalo_l.zip (275 MB)
3. Extract the zip file
4. Find the file: w600k_r50.onnx or similar face recognition model
5. Convert it to TFLite format OR use a pre-converted TFLite model
6. Rename it to: mobilefacenet.tflite
7. Place it in this directory: app/src/main/assets/ml_models/

ALTERNATIVE:
Search for "MobileFaceNet TFLite" online and download a pre-converted model.

The file should be named: mobilefacenet.tflite
And placed in: app/src/main/assets/ml_models/mobilefacenet.tflite

Once the file is in place, rebuild the app.
