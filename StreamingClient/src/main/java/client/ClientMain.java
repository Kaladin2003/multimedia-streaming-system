package client;

import fr.bmartel.speedtest.SpeedTestReport;
import fr.bmartel.speedtest.SpeedTestSocket;
import fr.bmartel.speedtest.inter.ISpeedTestListener;
import fr.bmartel.speedtest.model.SpeedTestError;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ClientMain extends JFrame {
    private JComboBox<String> videoList;
    private JButton fetchButton;
    private JButton streamButton;
    private JCheckBox bufferingCheckBox;
    private JComboBox<String> protocolComboBox;
    private JTextArea statsTextArea;
    private JLabel speedLabel;
    private long streamStartTime;
    private String currentVideo;
    private String currentProtocol;
    private double downloadSpeedMbps = 5.0; 

    public ClientMain() {
        setTitle("Streaming Client");
        setSize(1300, 600);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Top panel for controls
        JPanel controlPanel = new JPanel(new FlowLayout());
        videoList = new JComboBox<>();
        fetchButton = new JButton("Fetch Video List");
        streamButton = new JButton("Start Streaming");
        bufferingCheckBox = new JCheckBox("Enable Buffering");
        protocolComboBox = new JComboBox<>(new String[]{"UDP", "TCP", "RTP"});
        speedLabel = new JLabel("Connection Speed: Testing...");

        controlPanel.add(fetchButton);
        controlPanel.add(videoList);
        controlPanel.add(bufferingCheckBox);
        controlPanel.add(new JLabel("Protocol:"));
        controlPanel.add(protocolComboBox);
        controlPanel.add(streamButton);
        controlPanel.add(speedLabel);

        // Stats panel
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder("Streaming Statistics"));
        statsTextArea = new JTextArea();
        statsTextArea.setEditable(false);
        statsPanel.add(new JScrollPane(statsTextArea), BorderLayout.CENTER);

        add(controlPanel, BorderLayout.NORTH);
        add(statsPanel, BorderLayout.CENTER);

        fetchButton.addActionListener(this::fetchVideoList);
        streamButton.addActionListener(this::startStreaming);

        setVisible(true);

        
        runSpeedTest();
    }

    private void runSpeedTest() {
        updateStats("Starting network speed test...");
        speedLabel.setText("Connection Speed: Testing...");

        new Thread(() -> {
            try {
                SpeedTestSocket speedTestSocket = new SpeedTestSocket();
                speedTestSocket.setSocketTimeout(5000); 

                speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
                    @Override
                    public void onCompletion(SpeedTestReport report) {
                        downloadSpeedMbps = report.getTransferRateBit().doubleValue() / 1000000;
                        SwingUtilities.invokeLater(() -> {
                            speedLabel.setText(String.format("Connection Speed: %.2f Mbps", downloadSpeedMbps));
                            updateStats(String.format("Network speed test completed: %.2f Mbps", downloadSpeedMbps));
                            suggestOptimalSettings();
                        });
                    }

                    @Override
                    public void onError(SpeedTestError speedTestError, String errorMessage) {
                        SwingUtilities.invokeLater(() -> {
                            updateStats("Primary speed test failed: " + errorMessage);
                            tryFallbackSpeedTest();
                        });
                    }

                    @Override
                    public void onProgress(float percent, SpeedTestReport report) {
                       
                    }
                });

               
                speedTestSocket.startDownload("http://ipv4.ikoula.testdebit.info/10M.iso", 5000);

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    updateStats("Speed test initialization failed: " + e.getMessage());
                    tryFallbackSpeedTest();
                });
            }
        }).start();
    }

    private void tryFallbackSpeedTest() {
        new Thread(() -> {
            try {
                SpeedTestSocket speedTestSocket = new SpeedTestSocket();
                speedTestSocket.setSocketTimeout(5000);
                
                speedTestSocket.addSpeedTestListener(new ISpeedTestListener() {
                    @Override
                    public void onCompletion(SpeedTestReport report) {
                        downloadSpeedMbps = report.getTransferRateBit().doubleValue() / 1000000;
                        SwingUtilities.invokeLater(() -> {
                            speedLabel.setText(String.format("Connection Speed: %.2f Mbps", downloadSpeedMbps));
                            updateStats(String.format("Fallback speed test completed: %.2f Mbps", downloadSpeedMbps));
                            suggestOptimalSettings();
                        });
                    }

                    @Override
                    public void onError(SpeedTestError speedTestError, String errorMessage) {
                        SwingUtilities.invokeLater(() -> {
                            speedLabel.setText("Connection Speed: Unknown");
                            updateStats("All speed test servers failed. Using default speed (5 Mbps).");
                            downloadSpeedMbps = 5.0;
                            suggestOptimalSettings();
                        });
                    }

                    @Override
                    public void onProgress(float percent, SpeedTestReport report) {
                      
                    }
                });

                //alternative test servers
                String[] fallbackServers = {
                    "http://speedtest.tele2.net/10MB.zip",
                    "http://test.belwue.net/100M",
                    "http://speedtest.fremont.linode.com/100MB-fremont.bin"
                };

                for (String server : fallbackServers) {
                    try {
                        updateStats("Trying fallback server: " + server);
                        speedTestSocket.startDownload(server, 5000);
                        return; 
                    } catch (Exception e) {
                        updateStats("Fallback server failed: " + server);
                    }
                }
                
                throw new Exception("All fallback servers failed");

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    speedLabel.setText("Connection Speed: Unknown");
                    updateStats("All speed test attempts failed: " + e.getMessage());
                    downloadSpeedMbps = 5.0; // Default to 5 Mbps
                    suggestOptimalSettings();
                });
            }
        }).start();
    }
    // 
    private void suggestOptimalSettings() {
        String suggestion;
        if (downloadSpeedMbps > 10) {
            suggestion = "High-speed connection detected. You can stream high-quality videos.";
        } else if (downloadSpeedMbps > 5) {
            suggestion = "Medium-speed connection. You can stream Medium-quality videos..";
        } else if (downloadSpeedMbps > 2) {
            suggestion = "Low-speed connection. Consider lower quality streams or enable buffering.";
        } else {
            suggestion = "Very slow connection. Streaming may not work properly.";
        }
        
        updateStats("Recommendation: " + suggestion);
        
        // enable buffering 
        if (downloadSpeedMbps < 5) {
            SwingUtilities.invokeLater(() -> {
                bufferingCheckBox.setSelected(true);
                updateStats("Auto-enabled buffering for better streaming experience");
            });
        }
    }

    private void fetchVideoList(ActionEvent e) {
        try (Socket socket = new Socket("localhost", 9000);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            dos.writeUTF("LIST");

            int count = dis.readInt();
            videoList.removeAllItems();
            for (int i = 0; i < count; i++) {
                videoList.addItem(dis.readUTF());
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to fetch video list.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startStreaming(ActionEvent e) {
        String selected = (String) videoList.getSelectedItem();
        if (selected == null) return;

        currentVideo = selected;
        currentProtocol = (String) protocolComboBox.getSelectedItem();
        if (currentProtocol == null) currentProtocol = "UDP";

        updateStats(String.format("Starting stream with current network speed: %.2f Mbps", downloadSpeedMbps));

        String urlPrefix;
        switch (currentProtocol.toUpperCase()) {
            case "TCP":
                urlPrefix = "tcp://127.0.0.1:1234";
                break;
            case "RTP":
                urlPrefix = ""; 
                break;
            default:
                urlPrefix = "udp://127.0.0.1:1234";
        }

        streamStartTime = System.currentTimeMillis();
        updateStats("Stream started: " + currentVideo + " (" + currentProtocol + ")");
        updateStats("Buffering: " + (bufferingCheckBox.isSelected() ? "Enabled" : "Disabled"));
        updateStats("Start time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(streamStartTime)));

        new Thread(() -> {
            try (Socket socket = new Socket("localhost", 9000);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                dos.writeUTF("STREAM");
                dos.writeUTF(selected);
                dos.writeUTF(getLocalIPAddress());
                dos.writeUTF(currentProtocol);
                dos.writeDouble(downloadSpeedMbps); 

                ArrayList<String> command = new ArrayList<>();
                command.add("ffplay");
                command.add("-x");
                command.add("854");
                command.add("-y");
                command.add("480");
                command.add("-loglevel");
                command.add("debug");
                command.add("-stats");

                if (bufferingCheckBox.isSelected()) {
                    command.add("-fflags");
                    command.add("+genpts");
                    command.add("-probesize");
                    command.add("500000");
                    command.add("-analyzeduration");
                    command.add("1000000");
                    command.add("-buffer_size");
                    command.add("1000000");
                } else {
                    command.add("-fflags");
                    command.add("nobuffer");
                }

                if (currentProtocol.equalsIgnoreCase("RTP")) {
                    File sdpFile = File.createTempFile("stream_", ".sdp");
                    sdpFile.deleteOnExit();

                    StringBuilder sdpContent = new StringBuilder(dis.readUTF());
                    String line;
                    while (!(line = dis.readUTF()).equals("END_SDP")) {
                        sdpContent.append("\n").append(line);
                    }

                    try (BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(new FileOutputStream(sdpFile), StandardCharsets.UTF_8))) {
                        writer.write(sdpContent.toString());
                        writer.flush();
                    }

                    command.add("-protocol_whitelist");
                    command.add("file,udp,rtp");
                    command.add("-i");
                    command.add(sdpFile.getAbsolutePath());

                    updateStats("SDP file created at: " + sdpFile.getAbsolutePath());
                } else {
                    command.add("-i");
                    command.add(urlPrefix);
                }

                updateStats("Executing: " + String.join(" ", command));

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("bitrate=") || line.contains("fps=") || line.contains("speed=")) {
                                updateStats(line.trim());
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }).start();

                int exitCode = process.waitFor();
                long streamEndTime = System.currentTimeMillis();
                long duration = (streamEndTime - streamStartTime) / 1000;
                
                updateStats("FFplay exited with code: " + exitCode);
                updateStats("Stream ended at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(streamEndTime)));
                updateStats("Total streaming duration: " + duration + " seconds");
                updateStats("----- Stream Summary -----");
                updateStats("Video: " + currentVideo);
                updateStats("Protocol: " + currentProtocol);
                updateStats("Network Speed: " + String.format("%.2f Mbps", downloadSpeedMbps));
                updateStats("Buffering: " + (bufferingCheckBox.isSelected() ? "Enabled" : "Disabled"));
                updateStats("Duration: " + duration + " seconds");

            } catch (Exception ex) {
                ex.printStackTrace();
                updateStats("Error: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error starting stream.", "Streaming Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void updateStats(String message) {
        SwingUtilities.invokeLater(() -> {
            statsTextArea.append(message + "\n");
            statsTextArea.setCaretPosition(statsTextArea.getDocument().getLength());
        });
    }

    private String getLocalIPAddress() throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientMain::new);
    }
}