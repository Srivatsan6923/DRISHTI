import RPi.GPIO as GPIO
import time

# ---------------- GPIO SETUP ----------------
GPIO.setmode(GPIO.BCM)

TRIG = 23
ECHO = 24
BUZZER = 21

GPIO.setup(TRIG, GPIO.OUT)
GPIO.setup(ECHO, GPIO.IN)
GPIO.setup(BUZZER, GPIO.OUT)

GPIO.output(TRIG, False)
GPIO.output(BUZZER, False)

print("Ultrasonic distance + buzzer control")
time.sleep(2)

# ---------------- PARAMETERS ----------------
THRESHOLD_CM = 20.0   # adjust this distance

# ---------------- FUNCTIONS ----------------
def get_distance():
    # Trigger pulse
    GPIO.output(TRIG, True)
    time.sleep(0.00001)
    GPIO.output(TRIG, False)

    timeout = time.time() + 0.04  # 40 ms

    # Wait for echo HIGH
    while GPIO.input(ECHO) == 0:
        pulse_start = time.time()
        if pulse_start > timeout:
            return None

    # Wait for echo LOW
    while GPIO.input(ECHO) == 1:
        pulse_end = time.time()
        if pulse_end > timeout:
            return None

    pulse_duration = pulse_end - pulse_start
    distance = pulse_duration * 17150
    return round(distance, 2)

# ---------------- MAIN LOOP ----------------
try:
    while True:
        distance = get_distance()

        if distance is None:
            print("No echo received")
            GPIO.output(BUZZER, False)

        else:
            print(f"Distance: {distance} cm")

            if distance < THRESHOLD_CM:
                GPIO.output(BUZZER, True)
            else:
                GPIO.output(BUZZER, False)

        time.sleep(0.1)

except KeyboardInterrupt:
    print("\nStopped by user")

finally:
    GPIO.cleanup()
