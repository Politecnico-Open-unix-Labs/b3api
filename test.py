from websocket import create_connection
ws = create_connection("ws://0.0.0.0:8080")
ws.send('{"test":{"a":2},"key":"w4M8hFsB0PAYQQO9FelpQ98rPXCe8ifA"}')
print(ws.recv())
ws.close()
