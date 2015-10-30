from websocket import create_connection
ws = create_connection("ws://localhost:8080")
ws.send('{"hello":"world"}')
print(ws.recv())
