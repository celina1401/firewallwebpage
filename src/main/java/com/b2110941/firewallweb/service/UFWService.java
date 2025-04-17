package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.FirewallRule;
import com.b2110941.firewallweb.model.LoggingUFW;
import com.b2110941.firewallweb.model.PC;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UFWService {

    private static final Logger logger = LoggerFactory.getLogger(UFWService.class);

    @Autowired
    private ConnectSSH connectSSH;

    @Autowired
    private PCService pcService;

    private final Map<String, Map<String, LoggingUFW>> logCache = new HashMap<>();

    public String addRuleFromForm(PC pc, String action, boolean isOutgoing,
            String protocol, String toType, String toIp,
            String toRangeStart, String toRangeEnd, String port,
            String fromType, String fromIp, String app) {

        StringBuilder outBuilder = new StringBuilder();
        String result;
        String direction = isOutgoing ? "out" : "in";

        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            StringBuilder cmdBuilder = new StringBuilder("echo '")
                    .append(pc.getPassword())
                    .append("' | sudo -S ufw ")
                    .append(action).append(" ")
                    .append(direction).append(" ");

            // ------------------ Handle Interface ------------------
            if ("interface".equals(toType)) {
                if (app == null || app.isEmpty()) {
                    return "error: Interface name is required";
                }
                cmdBuilder.append("on ").append(app).append(" ");
            }

            // ------------------ Handle FROM clause ------------------
            if ("ipaddress".equals(fromType)) {
                if (fromIp == null || fromIp.isEmpty()) {
                    return "error: Source IP address is required for fromType 'ipaddress'";
                }
                cmdBuilder.append("from ").append(fromIp).append(" ");
            } else {
                cmdBuilder.append("from any ");
            }

            // ------------------ Handle TO clause ------------------
            if ("ipaddress".equals(toType)) {
                if (toIp == null || toIp.isEmpty()) {
                    return "error: Destination IP address is required for toType 'ipaddress'";
                }
                cmdBuilder.append("to ").append(toIp).append(" ");
            } else if ("range".equals(toType)) {
                if (toRangeStart == null || toRangeStart.isEmpty()) {
                    return "error: Range start is required for toType 'range'";
                }
                cmdBuilder.append("to ").append(toRangeStart).append(" ");
            } else if (!"app".equals(toType)) {
                cmdBuilder.append("to any ");
            }

            // ------------------ Handle App ------------------
            if ("app".equals(toType)) {
                if (app == null || app.isEmpty()) {
                    return "error: Application name is required for toType 'app'";
                }
                cmdBuilder.append("app \"").append(app).append("\" ");
            }

            // ------------------ Handle Port and Protocol ------------------
            if (port != null && !port.isEmpty()) {
                cmdBuilder.append("port ").append(port).append(" ");
            } else if (toRangeStart != null && !toRangeStart.isEmpty()
                    && toRangeEnd != null && !toRangeEnd.isEmpty()) {
                if ("none".equals(protocol)) {
                    return "error: Port range requires a protocol (tcp/udp)";
                }
                cmdBuilder.append("port ").append(toRangeStart).append(":").append(toRangeEnd).append(" ");
                logger.info("Adding port range: {}-{}", toRangeStart, toRangeEnd);
            }

            if (!"none".equals(protocol) && protocol != null) {
                cmdBuilder.append("proto ").append(protocol).append(" ");
            }

            // ------------------ Execute Command ------------------
            String command = cmdBuilder.toString().trim();
            logger.info("Executing UFW command: {}", command);

            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed())
                    break;
            }

            String output = outBuilder.toString();
            logger.info("Command output: {}", output);

            if (output.contains("Rule added") || output.contains("Skipping")) {
                result = "success";
            } else if (output.contains("ERROR")) {
                result = "error: " + output;
            } else {
                result = "success"; // Mặc định thành công nếu không có lỗi rõ ràng
            }

            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error adding firewall rule", e);
            result = "error: " + e.getMessage();
        }

        return result;
    }

    public String deleteRule(PC pc, String ruleId) {
        String result = "";
        StringBuilder outBuilder = new StringBuilder();

        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw --force delete " + ruleId;
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
            }

            String output = outBuilder.toString();
            logger.info("Delete command output: {}", output);

            if (output.contains("Rule deleted")) {
                result = "success";
            } else {
                result = "error: " + output;
            }

            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error deleting firewall rule", e);
            result = "error: " + e.getMessage();
        }

        return result;
    }

    public String getUFWStatus(PC pc) {
        String result = "inactive";
        StringBuilder outBuilder = new StringBuilder();

        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw status | grep Status";

            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
            }

            String output = outBuilder.toString();
            if (output.contains("active")) {
                result = "active";
            }

            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error getting UFW status", e);
        }

        return result;
    }

    public String toggleUFW(PC pc, boolean enable) {
        String result = "";
        StringBuilder outBuilder = new StringBuilder();

        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw " +
                    (enable ? "--force enable" : "--force disable");

            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
            }

            String output = outBuilder.toString();
            if ((enable && output.contains("enabled")) || (!enable && output.contains("disabled"))) {
                result = "success";
            } else {
                result = "error: " + output;
            }

            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error toggling UFW", e);
            result = "error: " + e.getMessage();
        }

        return result;
    }

    public FirewallRule[] getAllRules(PC pc) {
        return new FirewallRule[0]; // Placeholder
    }

    public String[] getUFWAppList(PC pc) {
        StringBuilder outBuilder = new StringBuilder();
        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw app list";
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
            }
            String output = outBuilder.toString();
            System.out.println(output);

            String[] lines = output.split("\n");
            java.util.List<String> appList = new java.util.ArrayList<>();
            boolean started = false;
            for (String line : lines) {
                if (line.trim().startsWith("Available applications:")) {
                    started = true;
                    continue;
                }
                if (started && !line.trim().isEmpty()) {
                    appList.add(line.trim());
                }
            }
            return appList.toArray(new String[0]);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[0]; // Placeholder
    }

    public String getUFWLogging(PC pc) {
        StringBuilder outBuilder = new StringBuilder();
        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw status verbose | grep Logging";
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed())
                    break;
            }

            String output = outBuilder.toString().trim();
            if (!output.isEmpty()) {
                int start = output.indexOf("(");
                int end = output.indexOf(")");
                if (start >= 0 && end >= 0) {
                    return output.substring(start + 1, end);
                }
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            logger.error("Error getting UFW logging level", e);
            return "error: " + e.getMessage();
        }
        return "unknown";
    }

    public String getLoggingLevel(PC pc) {
        StringBuilder outBuilder = new StringBuilder();
        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw status verbose | grep Logging";
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed())
                    break;
            }

            String output = outBuilder.toString().trim();
            logger.info("Logging level output: {}", output);

            if (!output.isEmpty()) {
                // Extract level from output (e.g., "Logging: on (low)" -> "low")
                int start = output.indexOf("(");
                int end = output.indexOf(")");
                if (start >= 0 && end >= 0) {
                    return output.substring(start + 1, end).toLowerCase();
                }
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            logger.error("Error getting UFW logging level", e);
            return "error";
        }
        return "off";
    }

    public String changeLoggingLevel(PC pc, String level) {
        StringBuilder outBuilder = new StringBuilder();
        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw logging " + level.toLowerCase();
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed())
                    break;
            }

            channel.disconnect();
            session.disconnect();

            String output = outBuilder.toString();
            if (output.contains("Logging")) {
                return "success";
            } else {
                return "error: " + output;
            }

        } catch (Exception e) {
            logger.error("Error changing UFW logging level", e);
            return "error: " + e.getMessage();
        }
    }
    public LoggingUFW getLogDetailsByTimestamp(String pcName, String timestamp, String ownerUsername) throws Exception {
        // Check cache first
        Map<String, LoggingUFW> machineLogs = logCache.computeIfAbsent(pcName, k -> new HashMap<>());
        if (machineLogs.containsKey(timestamp)) {
            logger.info("Cache hit for log entry: pcName={}, timestamp={}", pcName, timestamp);
            return machineLogs.get(timestamp);
        }

        // Validate machine and owner
        PC computer = null;
        try {
            Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
            if (computerOptional.isEmpty()) {
                logger.error("Machine not found: pcName={}, ownerUsername={}", pcName, ownerUsername);
                throw new Exception("Machine not found: " + pcName);
            }
            computer = computerOptional.get();
        } catch (Exception e) {
            logger.error("Error validating machine: pcName={}, ownerUsername={}", pcName, ownerUsername, e);
            throw new Exception("Error validating machine: " + e.getMessage(), e);
        }

        Session sshSession = null;
        ChannelExec channel = null;
        BufferedReader reader = null;

        try {
            // Establish SSH connection
            sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword());
            logger.info("SSH connection established for pcName={}", pcName);

            // Execute command to fetch log entry matching the timestamp
            String command = "echo '" + computer.getPassword() + "' | sudo -S grep \"" + timestamp + "\" /var/log/ufw.log";
            channel = (ChannelExec) sshSession.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            channel.connect();
            logger.info("Executing command to fetch log: {}", command);

            // Parse the log entry
            String line;
            LoggingUFW logEntry = null;
            while ((line = reader.readLine()) != null) {
                logEntry = parseLogEntry(line);
                if (logEntry != null) {
                    logger.info("Log entry found for timestamp={}", timestamp);
                    break; // We only need the first match
                }
            }

            if (logEntry == null) {
                logger.warn("No log entry found for timestamp={}", timestamp);
                throw new Exception("Log entry not found for timestamp: " + timestamp);
            }

            // Cache the log entry
            machineLogs.put(timestamp, logEntry);
            logger.info("Log entry cached for pcName={}, timestamp={}", pcName, timestamp);
            return logEntry;

        } catch (JSchException | IOException e) {
            logger.error("Error fetching log details for pcName={}, timestamp={}", pcName, timestamp, e);
            throw new Exception("Error fetching log details: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    logger.debug("Failed to close BufferedReader", ignored);
                }
            }
            if (channel != null) {
                channel.disconnect();
            }
            if (sshSession != null) {
                sshSession.disconnect();
                logger.info("SSH session disconnected for pcName={}", pcName);
            }
        }
    }

    /**
     * Parses a UFW log line into a LoggingUFW object.
     *
     * @param logLine The raw log line from UFW log
     * @return LoggingUFW object with parsed details
     */
    private LoggingUFW parseLogEntry(String logLine) {
        if (logLine == null || logLine.trim().isEmpty()) {
            logger.warn("Empty or null log line provided");
            return null;
        }

        LoggingUFW logEntry = new LoggingUFW();
        logEntry.setFullLog(logLine);

        try {
            // Extract timestamp (e.g., "Apr 17 12:34:56")
            Pattern timestampPattern = Pattern.compile("^(\\w+\\s+\\d+\\s+\\d+:\\d+:\\d+)");
            Matcher timestampMatcher = timestampPattern.matcher(logLine);
            if (timestampMatcher.find()) {
                logEntry.setTimestamp(timestampMatcher.group(1));
            }

            // Extract action (e.g., "UFW BLOCK")
            Pattern actionPattern = Pattern.compile("\\[UFW\\s+(\\w+)\\]");
            Matcher actionMatcher = actionPattern.matcher(logLine);
            if (actionMatcher.find()) {
                logEntry.setAction(actionMatcher.group(1));
            }

            // Extract source IP (SRC=)
            Pattern srcIpPattern = Pattern.compile("SRC=(\\d+\\.\\d+\\.\\d+\\.\\d+)");
            Matcher srcIpMatcher = srcIpPattern.matcher(logLine);
            if (srcIpMatcher.find()) {
                logEntry.setSourceIp(srcIpMatcher.group(1));
            }

            // Extract destination IP (DST=)
            Pattern dstIpPattern = Pattern.compile("DST=(\\d+\\.\\d+\\.\\d+\\.\\d+)");
            Matcher dstIpMatcher = dstIpPattern.matcher(logLine);
            if (dstIpMatcher.find()) {
                logEntry.setDestinationIp(dstIpMatcher.group(1));
            }

            // Extract source port (SPT=)
            Pattern srcPortPattern = Pattern.compile("SPT=(\\d+)");
            Matcher srcPortMatcher = srcPortPattern.matcher(logLine);
            if (srcPortMatcher.find()) {
                logEntry.setSourcePort(srcPortMatcher.group(1));
            }

            // Extract destination port (DPT=)
            Pattern dstPortPattern = Pattern.compile("DPT=(\\d+)");
            Matcher dstPortMatcher = dstPortPattern.matcher(logLine);
            if (dstPortMatcher.find()) {
                logEntry.setDestinationPort(dstPortMatcher.group(1));
            }

            // Extract protocol (PROTO=)
            Pattern protoPattern = Pattern.compile("PROTO=(\\w+)");
            Matcher protoMatcher = protoPattern.matcher(logLine);
            if (protoMatcher.find()) {
                String protoValue = protoMatcher.group(1);
                // Map protocol numbers to names
                switch (protoValue) {
                    case "2":
                        protoValue = "IGMP";
                        break;
                    case "6":
                        protoValue = "TCP";
                        break;
                    case "17":
                        protoValue = "UDP";
                        break;
                }
                logEntry.setProtocol(protoValue);
            }

            // Extract interface (IN=)
            Pattern interfacePattern = Pattern.compile("IN=(\\w+)");
            Matcher interfaceMatcher = interfacePattern.matcher(logLine);
            if (interfaceMatcher.find()) {
                logEntry.setInterface(interfaceMatcher.group(1));
            }

            logger.debug("Parsed log entry: timestamp={}, action={}, srcIp={}, dstIp={}, srcPort={}, dstPort={}, proto={}, interface={}",
                    logEntry.getTimestamp(), logEntry.getAction(), logEntry.getSourceIp(), logEntry.getDestinationIp(),
                    logEntry.getSourcePort(), logEntry.getDestinationPort(), logEntry.getProtocol(), logEntry.getInterface());

        } catch (Exception e) {
            logger.error("Error parsing log entry: {}", logLine, e);
            return null;
        }

        return logEntry;
    }

}