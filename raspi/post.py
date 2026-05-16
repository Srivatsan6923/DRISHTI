import requests
import RPi.GPIO as GPIO
import time
from datetime import datetime
import cv2

BUTTON_PIN = 17

GPIO.setmode(GPIO.BCM)
GPIO.setup(BUTTON_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)

# ---------- Read URL from file ----------
with open("url.txt", "r") as f:
    ip = f.read().strip()

url = f"http://{ip}:5000/upload"
print("Upload URL:", url)
# ---------------------------------------

print("Ready. Press the button to take a photo.")

try:
    while True:
        if GPIO.input(BUTTON_PIN) == GPIO.LOW:  # Button pressed
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

            cap = cv2.VideoCapture(0)
            if not cap.isOpened():
                print("Cannot open camera")
                continue

            ret, frame = cap.read()
            cap.release()

            if not ret:
                print("Capture failed")
                continue

            success, buffer = cv2.imencode(".jpg", frame)
            if not success:
                print("Encoding failed")
                continue

            image_bytes = buffer.tobytes()
            print("Capture success")

            headers = {"Content-Type": "application/octet-stream"}
            response = requests.post(url, data=image_bytes, headers=headers)

            print(response.text)

            time.sleep(0.5)  # debounce
            while GPIO.input(BUTTON_PIN) == GPIO.LOW:
                time.sleep(0.1)

except KeyboardInterrupt:
    print("Exiting...")

finally:
    GPIO.cleanup()
