import json
import string
import random
import os


filename = "data/tokens.json"
alphabet = string.ascii_letters + string.digits + "/;<>-_+="
new_token = "".join([random.choice(alphabet) for n in range(32)])


try:
    with open(filename, "r") as f:
        data = json.load(f)
except:
    data = []


with open(filename, "w") as f:
    data.append(new_token)
    json.dump(data, f, indent=4)


print("Your new token:", new_token)
