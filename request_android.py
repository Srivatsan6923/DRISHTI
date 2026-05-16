import requests

url = "http://100.80.244.24:5000/upload"

image_path = "example.jpg"
with open(image_path, "rb") as f:
    image_bytes = f.read()

# Send raw bytes
headers = {"Content-Type": "application/octet-stream"}
response = requests.post(url, data=image_bytes, headers=headers)

print(response.text)


# import requests
# import json

# url = "http://10.95.218.122:5000/action"

# data = {
#     "command": "do_something",
#     "value": 42
# }

# headers = {"Content-Type": "application/json"}
# response = requests.post(url, data=json.dumps(data), headers=headers)

# print(response.text)