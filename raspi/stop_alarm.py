import requests
import RPi.GPIO as GPIO
import time

BUTTON_PIN = 27

GPIO.setmode(GPIO.BCM)
GPIO.setup(BUTTON_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)

# Read IP
with open("url.txt", "r") as f:
    ip = f.read().strip()

url = f"http://{ip}:5000/stopalarm"

print("Ready. Button 27 → Stop Alarm")

try:
    while True:
        if GPIO.input(BUTTON_PIN) == GPIO.LOW:
            print("Sending stop request...")

            try:
                response = requests.post(url, timeout=5)
                print("Response:", response.text)
            except Exception as e:
                print("Request failed:", e)

            # debounce
            time.sleep(0.4)
            while GPIO.input(BUTTON_PIN) == GPIO.LOW:
                time.sleep(0.1)

        time.sleep(0.05)

except KeyboardInterrupt:
    pass

finally:
    GPIO.cleanup()
