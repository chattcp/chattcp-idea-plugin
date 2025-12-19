# WebSocket Test Scripts

These scripts are for testing WebSocket packet capture and decoding functionality.

## Requirements

```bash
python3 -m pip install websockets
```

## Usage

### 1. Start the Server

```bash
python3 websocket_server.py
```

The server will:
- Listen on `ws://127.0.0.1:8080`
- Send messages every 2 seconds (JSON, text, and binary)
- Echo back any messages received from clients

### 2. Start the Client

In another terminal:

```bash
python3 websocket_client.py
```

The client will:
- Connect to the server
- Send messages every 3 seconds
- Display all received messages

### 3. Capture Traffic

Use your ChatTCP plugin to capture traffic on:
- Interface: `lo0` (loopback) or `127.0.0.1`
- Port: `8080`

## Message Types

The scripts send various message types to test decoding:

1. **JSON messages** - Structured data with metadata
2. **Text messages** - Plain text strings
3. **Binary messages** - Encoded binary data

## Customization

You can modify the scripts to:
- Change the port (default: 8080)
- Adjust message frequency
- Add more complex message types
- Test different payload sizes

## Example Output

**Server:**
```
Starting WebSocket server on ws://127.0.0.1:8080
[14:30:15] Client connected: ('127.0.0.1', 54321)
[14:30:17] Sent JSON: message #1
[14:30:19] Sent text: Simple text message #2...
[14:30:21] Sent binary: 20 bytes
```

**Client:**
```
Connecting to ws://127.0.0.1:8080...
[14:30:15] Connected to server
[14:30:17] Received [welcome]: {'type': 'welcome', 'message': 'Connected to WebSocket test server'}
[14:30:18] Sent: Client message #1
[14:30:19] Received [data]: {'type': 'data', 'id': 1, 'payload': 'Test message #1'}
```
