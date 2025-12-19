# ChatTCP Plugin for IntelliJ IDEA

ChatTCP 的 IntelliJ IDEA 插件。
https://plugins.jetbrains.com/vendor/chattcp

## 功能特性

- 🔍 **实时捕获** - 实时捕获 TCP 连接和数据包
- 🌐 **网络接口选择** - 支持选择不同的网络接口
- 🎯 **端口过滤** - 可指定监听的 TCP 端口
- 💬 **聊天式展示** - 以聊天对话形式展示数据包，直观易读
- 📊 **详细信息** - 显示 SEQ、标志位、时间戳、Payload 等详细信息
- 🔌 **WebSocket 支持** - 自动解码 WebSocket 协议数据包

## 技术栈

- Java 17
- IntelliJ Platform SDK
- pcap4j 1.8.2 (网络数据包捕获)
- jna 5.13.0

## 前置要求

### macOS
```bash
# libpcap 通常已预装，如果没有：
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
下载并安装 Npcap: https://npcap.com/

## 构建项目

```bash
./gradlew buildPlugin
```

## 运行插件

```bash
./gradlew runIde
```

## 安装

1. 构建插件: `./gradlew buildPlugin`
2. 在 IntelliJ IDEA 中: `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk`
3. 选择 `build/distributions/ChatTCP-Plugin-1.0.0.zip`

## 使用说明

1. 点击右侧工具栏的 ChatTCP 图标
2. 选择网络接口（如 en0, eth0 等）
3. 输入要监听的 TCP 端口（如 8080, 3000）
4. 点击"开始"按钮开始捕获
5. 在连接列表中选择要查看的连接
6. 在下方查看该连接的所有数据包
