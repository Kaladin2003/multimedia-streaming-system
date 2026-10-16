# Multimedia Video Streaming System

A Client-Server streaming application built with **Java** and **FFmpeg**. This project implements a robust video transmission system that adapts to network conditions and supports multiple transport protocols (TCP, UDP, RTP).

## Overview

Streaming multimedia content over a network requires handling various protocols and data formats efficiently. This tool establishes a **Server** that manages video storage and conversion, and a **Client** that requests streams based on user preference. It demonstrates the use of **Java Sockets** and **ProcessBuilder** to coordinate external media tools for real-time playback.

## System Logic & Features

The system respects the following real-world operational rules:

1.  **Protocol Flexibility:** Users can dynamically switch between **TCP** (reliable), **UDP** (fast), and **RTP** (real-time) protocols depending on their needs.
2.  **Automated Media Processing:** Upon startup, the server automatically scans for missing video formats (.avi, .mp4, .mkv) or resolutions (240p–1080p) and generates them using **FFmpeg**.
3.  **Network Adaptation:** The client runs a speed test on launch. If the connection is below 2 Mbps, it automatically enforces **Buffering** to prevent playback stutter.
4.  **Session Management:** For RTP streaming, the system dynamically generates and transfers **SDP (Session Description Protocol)** files to the client to establish the media session.
5.  **Structured Logging:** The server utilizes **Apache Log4j 2** to maintain a structured history of all client connections, errors, and stream requests.

## Technologies

* **Language:** Java 17+
* **Build:** Maven
* **Core tool:** [FFmpeg & FFplay](https://ffmpeg.org/) (media processing and playback)
* **Libraries:**
    * `org.apache.logging.log4j` 2.24 (server logging)
    * `fr.bmartel.jspeedtest` (network diagnostics)
* **GUI:** Java Swing

## Project structure

```text
StreamingServer/
  src/main/java/Server/ServerMain.java   # socket server, FFmpeg conversion and streaming
  Videos/                                # source videos (converted copies are generated on startup)
StreamingClient/
  src/main/java/client/ClientMain.java   # Swing client, speed test, FFplay playback
```

## Security

* The server streams only video files that exist directly inside `Videos/`. Requests such as `../secret.txt` are rejected.
* The client IP must be a valid IPv4 address before it is used in an FFmpeg URL.
* FFmpeg runs with an argument list, not a command string, so file names with spaces work and nothing is parsed by a shell.
* Log4j is pinned to 2.24.x. Versions before 2.17.1 are vulnerable to Log4Shell (CVE-2021-44228).

## Installation & usage

1. **Install prerequisites:** JDK 17+, Maven, and FFmpeg (with `ffmpeg` and `ffplay` on your PATH).

2. **Clone the repository:**
    ```bash
    git clone https://github.com/Kaladin2003/multimedia-streaming-system.git
    cd multimedia-streaming-system
    ```

3. **Run the server** (port 9000). On first start it converts every video in `Videos/` to all missing formats and resolutions:
    ```bash
    cd StreamingServer
    mvn compile exec:java
    ```

4. **Run the client** in a second terminal:
    ```bash
    cd StreamingClient
    mvn compile exec:java
    ```

5. In the client, click **Fetch Video List**, choose a video and a protocol (UDP, TCP or RTP), then click **Start Streaming**.

## License

This project is licensed under the [MIT License](LICENSE).
