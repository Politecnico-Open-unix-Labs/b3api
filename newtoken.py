from __future__ import print_function
import json
import string
import random
import sys
import re


# `data` is a dictionary token:authorized_path.
# If a command line argument (in the form of a filesystem path, e.g. `/foo/bar`)
# is provided, then the new token will be able to edit only that path.
# Default path is `/` (so all the data.)


def parse_path(p):
    return re.findall(r"[\w]+", p)


filename = "data/tokens.json"
alphabet = string.ascii_letters + string.digits + "/;<>-_+="
new_token = "".join([random.choice(alphabet) for n in range(32)])


try:
    with open(filename, "r") as f:
        data = json.load(f)
except:
    data = {}


with open(filename, "w") as f:
    if len(sys.argv) > 1:
        new_path = parse_path(sys.argv[1])
    else:
        new_path = []
    data[new_token] = new_path
    json.dump(data, f, indent=4)


print("Your new token:", new_token)
print("Authorized path:", "/" + "/".join(new_path))
