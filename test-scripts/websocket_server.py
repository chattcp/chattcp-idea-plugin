#!/usr/bin/env python3
"""
WebSocket Server for testing packet capture
Continuously sends messages to connected clients
"""

import asyncio
import websockets
import json
import time
import base64
from datetime import datetime

async def handle_client(websocket):
    client_address = websocket.remote_address
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Client connected: {client_address}")
    
    try:
        # Send welcome message
        await websocket.send(json.dumps({
            "type": "welcome",
            "message": "Connected to WebSocket test server",
            "timestamp": time.time()
        }))
        
        # Counter for messages
        message_count = 0
        
        # Start sending periodic messages
        async def send_periodic_messages():
            nonlocal message_count
            while True:
                message_count += 1
                
                # Send different types of messages
                if message_count % 3 == 0:
                    # JSON message - simple
                    data = {"msg": f"Message {message_count}", "id": message_count}
                    json_str = json.dumps(data)
                    await websocket.send(json_str)
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] Sent JSON: {json_str}")
                
                elif message_count % 3 == 1:
                    # Text message - simple and clear
                    text = f"Hello World {message_count}"
                    await websocket.send(text)
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] Sent text: '{text}'")
                
                else:
                    # Binary message - simple
                    binary_data = f"Binary{message_count}".encode('utf-8')
                    await websocket.send(binary_data)
                    base64_str = base64.b64encode(binary_data).decode('utf-8')
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] Sent binary: {len(binary_data)} bytes, base64: {base64_str}")
                
                await asyncio.sleep(2)  # Send every 2 seconds
        
        # Start sending messages
        send_task = asyncio.create_task(send_periodic_messages())
        
        # Handle incoming messages from client
        async for message in websocket:
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Received from client: {message[:100]}")
            
            # Echo back with modification
            response = {
                "type": "echo",
                "original": message[:100] if len(message) > 100 else message,
                "timestamp": time.time()
            }
            await websocket.send(json.dumps(response))
    
    except websockets.exceptions.ConnectionClosed:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Client disconnected: {client_address}")
    except Exception as e:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] Error: {e}")
    finally:
        if 'send_task' in locals():
            send_task.cancel()

async def main():
    host = "127.0.0.1"
    port = 8080
    
    print(f"Starting WebSocket server on ws://{host}:{port}")
    print("Compression: DISABLED")
    print("Press Ctrl+C to stop\n")
    
    # Disable compression by setting compression=None
    async with websockets.serve(handle_client, host, port, compression=None):
        await asyncio.Future()  # Run forever

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nServer stopped")
