# b3api

FIXME: description

## Installation

Download from http://example.com/FIXME.

## Usage

FIXME: explanation

    $ java -jar b3api-0.1.0-standalone.jar [args]

## Options

FIXME: listing of options this app accepts.

## Testing

### Websockets

```python
from websocket import create_connection
ws = create_connection("ws://localhost:8080")
ws.send('{"hello":"world", "key":<YOUR_TOKEN_HERE>}')
print(ws.recv())
ws.close()
```

### cURL
```bash
curl localhost:8080 # GET
curl -H "Content-Type: application/json" -X POST \
  -d '{"hello":"world","key":<YOUR_TOKEN_HERE>}' http://localhost:8080 # POST

```

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
