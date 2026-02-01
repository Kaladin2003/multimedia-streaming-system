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

* **Language:** Java (JDK 21+)
* **Core Tool:** [FFmpeg & FFplay](https://ffmpeg.org/) (Media processing & playback)
* **Libraries:**
    * `org.apache.logging.log4j` (Server Logging)
    * `fr.bmartel.speedtest` (Network Diagnostics)
* **GUI:** Java Swing

## Installation & Usage

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/Kaladin2003/multimedia-streaming-system.git](https://github.com/Kaladin2003/multimedia-streaming-system.git)
    cd multimedia-streaming-system
    ```

2.  **Prerequisites:**
    * Ensure **FFmpeg** is installed and added to your system PATH.
    * Install Maven dependencies using your IDE or command line.

3.  **Run the Server:**
    ```bash
    # Navigate to Server directory and run ServerMain
    java Server.ServerMain
    ```
    *The server will start on port 9000 and convert missing videos.*

4.  **Run the Client:**
    ```bash
    # Navigate to Client directory and run ClientMain
    java client.ClientMain
    ```
