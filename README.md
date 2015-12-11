# b3api

Server side implementation of a super-set of the
[SpaceAPI](http://spaceapi.net/),
keeps the informations about the hackerspace status and send them to the clients.

Can be also used as server backend for Internet of Things and domotics
applications.


## Usage

Run b3api on your server (e.g. `localhost:8080`).

It stores informations in a dictionary, to get them you can simply use a GET
request
```bash
curl localhost:8080
```

To send data you can use a POST, but you need a token
```bash
curl -H "Content-Type: application/json" -X POST \
  -d '{"hello":"world","key":<YOUR_TOKEN_HERE>}' http://localhost:8080
```

To generate new tokens run `newkoken.py`.

Tokens can authorize writings on restricted subpath (e.g. `/foo/bar`) of the
data dictionary, to generate this ones run:
```bash
python newtoken.py /foo/bar
```


### Websockets

You can also use websockets:
```python
from websocket import create_connection

ws = create_connection("ws://localhost:8080")

# receiving
print(ws.recv())

# sending
ws.send('{"hello":"world", "key":<YOUR_TOKEN_HERE>}')

ws.close()
```

If you keep the socket openened and use callback you will receive a diffential
update every time a field is changed.

To use websockets in python install `websocket-client`
```bash
pip install websocket-client
```

For further explainations read
[the documentation](https://pypi.python.org/pypi/websocket-client/).


## License

See [LICENSE file](https://github.com/Politecnico-Open-unix-Labs/b3api/blob/master/LICENSE).
