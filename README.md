# ChatTCP for IntelliJ IDEA

Enable fast TCP packet capture and viewing in IntelliJ IDEA, improving the efficiency of microservice interface troubleshooting.

https://plugins.jetbrains.com/vendor/chattcp

## Features

- 🔍 **Real-time Capture** - Capture TCP connections and packets in real-time
- 🌐 **Network Interface Selection** - Support for selecting different network interfaces
- 🎯 **Port Filtering** - Specify TCP ports to monitor
- 💬 **Chat-style Display** - Display packets in a chat conversation format for intuitive reading
- 📊 **Detailed Information** - Show SEQ, flags, timestamps, payload, and other detailed information
- 🔌 **WebSocket Support** - Automatically decode WebSocket protocol packets

## Tech Stack

- Java 17
- IntelliJ Platform SDK
- pcap4j 1.8.2 (Network packet capture)
- jna 5.13.0

## Prerequisites

### macOS
```bash
# libpcap is usually pre-installed, if not:
brew install libpcap
```

### Linux (Ubuntu/Debian)
```bash
sudo apt-get install libpcap-dev
```

### Linux (CentOS/RHEL)
```bash
sudo yum install libpcap-devel
```

### Windows
Download and install Npcap: https://npcap.com/

## Build

```bash
./gradlew buildPlugin
```

## Run Plugin

```bash
./gradlew runIde
```

## Installation

1. Build the plugin: `./gradlew buildPlugin`
2. In IntelliJ IDEA: `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk`
3. Select `build/distributions/ChatTCP-Plugin-*.*.*.zip`

## Usage

1. Click the ChatTCP icon in the right toolbar
2. Select a network interface (e.g., en0, eth0)
3. Enter the TCP port to monitor (e.g., 8080, 3000)
4. Click the "Start" button to begin capturing
5. Select a connection from the connection list
6. View all packets for that connection below

## Contributing

Contributions are welcome! Please note:
- Submitted code will be licensed under AGPL-3.0
- Please open an issue first for major changes
- Follow the existing code style

## Contact

- Email: wujiuye99@gmail.com
- Plugin Homepage: https://plugins.jetbrains.com/vendor/chattcp
