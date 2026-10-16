package Server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ServerMain {
    // Logger for server
    private static final Logger logger = LogManager.getLogger(ServerMain.class);
    

    private static final int PORT = 9000;
    private static final String VIDEO_FOLDER = "Videos";
    private static final String[] FORMATS = {"mp4", "mkv", "avi"};
    private static final String[] RESOLUTIONS = {"240p", "360p", "480p", "720p", "1080p"};
    // IPv4 literal, so a hostname cannot trigger a DNS lookup
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}");

    public static void main(String[] args) {
        logger.info("Starting server and checking for missing video formats...");
        convertVideosIfMissing();  

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server listening on port {}", PORT);

            // client connection
            while (true) {
                Socket client = serverSocket.accept();
                logger.info("New client connected: {}", client.getInetAddress());
                new Thread(() -> handleClient(client)).start(); 
            }
        } catch (IOException e) {
            logger.error("Server encountered an error: ", e);
        }
    }

   
    private static void handleClient(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            String command = dis.readUTF();
            logger.info("Received command: {}", command);

            if (command.equals("LIST")) {
                // Send  video list
                File folder = new File(VIDEO_FOLDER);
                File[] files = folder.listFiles((dir, name) -> 
                    name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi"));

                if (files == null) {
                    logger.warn("No video files found in directory: {}", VIDEO_FOLDER);
                    dos.writeInt(0);
                    return;
                }

                dos.writeInt(files.length);
                for (File file : files) {
                    dos.writeUTF(file.getName());
                }

            } else if (command.equals("STREAM")) {
                
                String fileName = dis.readUTF();
                String clientIP = dis.readUTF();
                String protocol = dis.readUTF();

                // Reject path traversal (e.g. "../../secret.txt") and unknown files
                File videoFile = new File(VIDEO_FOLDER, fileName);
                if (!isVideoInFolder(videoFile)) {
                    logger.warn("Rejected stream request for invalid file: {}", fileName);
                    return;
                }
                if (!isValidIPv4(clientIP)) {
                    logger.warn("Rejected stream request with invalid client IP: {}", clientIP);
                    return;
                }

                String filePath = videoFile.getPath();
                String sdpFileName = "stream_" + clientIP.replace(".", "_") + ".sdp";

                // Pass arguments as a list: no shell parsing, and paths with spaces work
                List<String> ffmpegCommand = new ArrayList<>(List.of("ffmpeg", "-re", "-i", filePath));
                switch (protocol.toLowerCase()) {
                    case "udp":
                        ffmpegCommand.addAll(List.of("-f", "avi", "udp://" + clientIP + ":1234"));
                        break;
                    case "tcp":
                        ffmpegCommand.addAll(List.of("-f", "avi", "tcp://0.0.0.0:1234?listen"));
                        break;
                    case "rtp":
                        // SDP file for RTP
                        String sdpContent = createSDPContent(clientIP);
                        dos.writeUTF(sdpContent);
                        dos.writeUTF("END_SDP");
                        dos.flush();

                        ffmpegCommand.addAll(List.of("-an", "-c:v", "copy", "-f", "rtp",
                            "-sdp_file", sdpFileName, "rtp://" + clientIP + ":5004?rtcpport=5008"));
                        break;
                    default:
                        logger.error("Unsupported protocol: {}", protocol);
                        return;
                }

                // Start FFmpeg streaming process
                logger.info("Executing ffmpeg command: {}", String.join(" ", ffmpegCommand));
                ProcessBuilder pb = new ProcessBuilder(ffmpegCommand);
                pb.inheritIO();
                pb.start();

                if (protocol.equalsIgnoreCase("rtp")) {
                    
                    deliverSDPFile(sdpFileName, dos);
                }
            }

        } catch (Exception e) {
            logger.error("Error handling client: ", e);
        }
    }

    
    private static void convertVideosIfMissing() {
        File folder = new File(VIDEO_FOLDER);
        if (!folder.exists()) return;

        
        File[] originals = folder.listFiles((dir, name) ->
            name.matches(".*\\.(mp4|mkv|avi)")
                && !name.matches(".*[-_](240p|360p|480p|720p|1080p)\\.(mp4|mkv|avi)"));

        if (originals == null) return;

        //  resolution/format 
        for (File original : originals) {
            String cleanedName = getCleanBaseName(original.getName());
            for (String res : RESOLUTIONS) {
                String scale = getScale(res);
                for (String format : FORMATS) {
                    String newName = cleanedName + "-" + res + "." + format;
                    File outputFile = new File(folder, newName);
                    if (outputFile.exists()) continue;

                    
                    convertVideo(original, outputFile, scale);
                }
            }
        }
    }

    // Video conversion
    private static void convertVideo(File input, File output, String scale) {
        List<String> cmd = List.of("ffmpeg", "-i", input.getAbsolutePath(),
            "-vf", "scale=" + scale, output.getAbsolutePath());

        try {
            logger.info("Creating: {}", output.getName());
            Process process = new ProcessBuilder(cmd).inheritIO().start();
            process.waitFor();
        } catch (Exception e) {
            logger.error("Error converting video {}: ", input.getName(), e);
        }
    }

    // sdp file 
    private static String createSDPContent(String clientIP) {
        return String.format(
            "v=0\n" +
            "o=- 0 0 IN IP4 %s\n" +
            "s=No Name\n" +
            "c=IN IP4 %s\n" +
            "t=0 0\n" +
            "a=tool:libavformat\n" +
            "m=video 5004 RTP/AVP 96\n" +
            "b=AS:3100\n" +
            "a=rtpmap:96 MP4V-ES/90000\n" +
            "a=fmtp:96 profile-level-id=1\n",
            clientIP, clientIP);
    }

    //  SDP file delivery 
    private static void deliverSDPFile(String sdpFileName, DataOutputStream dos) 
        throws InterruptedException, IOException {
        Thread.sleep(1000); 
        File sdpFile = new File(sdpFileName);
        if (sdpFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(sdpFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    dos.writeUTF(line);
                }
            }
        } else {
            logger.error("SDP file not found: {}", sdpFileName);
        }
        dos.writeUTF("END_SDP");
    }

    // Resolution to FFmpeg 
    private static String getScale(String resolution) {
        switch (resolution) {
            case "240p": return "426:240";
            case "360p": return "640:360";
            case "480p": return "854:480";
            case "720p": return "1280:720";
            case "1080p": return "1920:1080";
            default: return "854:480";  // Default to 480p
        }
    }

    
    // True only for an existing video file directly inside VIDEO_FOLDER
    private static boolean isVideoInFolder(File file) throws IOException {
        File folder = new File(VIDEO_FOLDER).getCanonicalFile();
        File canonical = file.getCanonicalFile();
        return folder.equals(canonical.getParentFile())
            && canonical.isFile()
            && canonical.getName().matches(".*\\.(mp4|mkv|avi)");
    }

    private static boolean isValidIPv4(String ip) {
        if (ip == null || !IPV4.matcher(ip).matches()) return false;
        for (String part : ip.split("\\.")) {
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }

    private static String getCleanBaseName(String fileName) {
        String name = fileName.replaceAll("[-_](240p|360p|480p|720p|1080p)", "");
        return name.replaceAll("\\.[^.]+$", "");  
    }
}