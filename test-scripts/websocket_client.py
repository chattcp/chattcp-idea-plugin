#!/usr/bin/env python3
"""
WebSocket Client for testing packet capture
Connects to server and exchanges messages
"""

import asyncio
import websockets
import json
import time
from datetime import datetime

async def send_messages(websocket):
    """Send periodic messages to server"""
    message_count = 0
    
    while True:
        await asyncio.sleep(3)  # Send every 3 seconds
        message_count += 1
        
        # Send simple text messages
        text = f"Client{message_count}"
        await websocket.send(text)
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Sent: '{text}'")

async def receive_messages(websocket):
    """Receive and display messages from server"""
    async for message in websocket:
        try:
            # Try to parse as JSON
            data = json.loads(message)
            msg_type = data.get('type', 'unknown')
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Received [{msg_type}]: {str(data)[:100]}")
        except json.JSONDecodeError:
            # Plain text or binary
            if isinstance(message, bytes):
                print(f"[{datetime.now().strftime('%H:%M:%S')}] Received binary: {len(message)} bytes")
            else:
                print(f"[{datetime.now().strftime('%H:%M:%S')}] Received text: {message[:100]}")

async def main():
    uri = "ws://127.0.0.1:8080"
    
    print(f"Connecting to {uri}...")
    print("Compression: DISABLED")
    print("Press Ctrl+C to stop\n")
    
    try:
        # Disable compression by setting compression=None
        async with websockets.connect(uri, compression=None) as websocket:
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Connected to server\n")
            
            # Run send and receive concurrently
            await asyncio.gather(
                send_messages(websocket),
                receive_messages(websocket)
            )
    
    except websockets.exceptions.ConnectionClosed:
        print("\nConnection closed by server")
    except ConnectionRefusedError:
        print(f"\nError: Could not connect to {uri}")
        print("Make sure the server is running first!")
    except Exception as e:
        print(f"\nError: {e}")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nClient stopped")
