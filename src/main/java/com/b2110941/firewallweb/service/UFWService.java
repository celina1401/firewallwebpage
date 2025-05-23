package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.FirewallRule;
import com.b2110941.firewallweb.model.PC;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private UbuntuInfo ubuntuInfo;

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

            if (output.contains("Rule added")) {
                result = "added";
            } else if (output.contains("existing")) {
                result = "existing";
            } else {
                result = "notfound"; // Mặc định thành công nếu không có lỗi rõ ràng
            }

            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error adding firewall rule", e);
            result = "error: " + e.getMessage();
        }

        return result;
    }

    public String deleteRule(PC pc, String uiIdStr) {
        try {
            int uiId = Integer.parseInt(uiIdStr);
            // Map<Integer,String> rules = fetchAddedRules(pc);
            // if (!rules.containsKey(uiId)) {
            //     return "error: rule id " + uiId + " not found";
            // }
            String cmd = "echo '" + pc.getPassword() + "' | sudo -S ufw --force delete " + uiId;
            String output = ubuntuInfo.executeCommand(
                connectSSH.establishSSH(
                    pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword()
                ), cmd
            );
            if (output.toLowerCase().contains("deleted")) {
                return "success";
            } else {
                return "error: " + output;
            }
        } catch (Exception e) {
            logger.error("Error deleting rule", e);
            return "error: " + e.getMessage();
        }
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

    // logging status
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

    public Map<String,String> fetchRuleDetailsById(PC pc, String uiIdStr) {
        try {
            int uiId = Integer.parseInt(uiIdStr);
            Map<Integer,String> rules = fetchAddedRules(pc);
            String line = rules.get(uiId);
            if(line == null) return null;
    
            Map<String,String> m = new HashMap<>();
            m.put("id", uiIdStr);
    
            // parse action
            Matcher actM = Pattern.compile("\\bufw\\s+(allow|deny)\\b")
                                  .matcher(line);
            m.put("action", actM.find() ? actM.group(1) : "");
    
            // parse direction, interface, protocol, from, to, port/app...
            m.put("direction", line.contains(" out ") ? "out" : "in");
    
            Matcher ifM = Pattern.compile("\\bon\\s+(\\S+)").matcher(line);
            m.put("interface", ifM.find() ? ifM.group(1) : "");
    
            Matcher protoM = Pattern.compile("\\bproto\\s+(\\w+)").matcher(line);
            m.put("protocol", protoM.find() ? protoM.group(1) : "");
    
            Matcher fromM = Pattern.compile("\\bfrom\\s+(\\S+)").matcher(line);
            m.put("sourceIp", fromM.find() ? fromM.group(1) : "any");
    
            Matcher toM = Pattern.compile("\\bto\\s+(\\S+)").matcher(line);
            m.put("destinationIp", toM.find() ? toM.group(1) : "any");
    
            Matcher appM = Pattern.compile("\\bapp\\s+['\"]([^'\"]+)['\"]")
                                   .matcher(line);
            m.put("app", appM.find() ? appM.group(1) : "");
    
            // port / port range
            Matcher pr = Pattern.compile("\\bport\\s+(\\d+):(\\d+)").matcher(line);
            if(pr.find()) {
                m.put("portType","range");
                m.put("portStart", pr.group(1));
                m.put("portEnd", pr.group(2));
                m.put("port","");
            } else {
                Matcher ps = Pattern.compile("\\bport\\s+(\\d+)").matcher(line);
                if(ps.find()) {
                    m.put("portType","specific");
                    m.put("port", ps.group(1));
                } else {
                    m.put("portType","any");
                    m.put("port","");
                }
                m.put("portStart",""); m.put("portEnd","");
            }
    
            return m;
        } catch(Exception e){
            logger.error("Error fetching rule details", e);
            return null;
        }
    }
    

    public String getRawAddedRuleLine(PC pc, String ruleId) {
        String command = "echo '" + pc.getPassword() + "' | sudo -S ufw show added | sed -n '"
                + ruleId + "p'";
        try {
            Session session = connectSSH.establishSSH(
                    pc.getIpAddress(), pc.getPort(), pc.getPcUsername(), pc.getPassword());
            String output = ubuntuInfo.executeCommand(session, command);
            session.disconnect();
            return output == null ? "" : output.trim();
        } catch (Exception e) {
            logger.error("Error fetching raw added rule line", e);
            return "";
        }
    }

    public Map<Integer,String> fetchAddedRules(PC pc) throws Exception {
        // 1) Lấy output
        Session session = connectSSH.establishSSH(
            pc.getIpAddress(), pc.getPort(), pc.getPcUsername(), pc.getPassword());
        String output = ubuntuInfo.executeCommand(session, "echo '" + pc.getPassword() + "' | sudo -S ufw show added");
        session.disconnect();
    
        // 2) Split từng dòng
        String[] lines = output.split("\\r?\\n");
        Map<Integer,String> rules = new LinkedHashMap<>();
        // 3) Bỏ header (dòng đầu tiên), đánh index từ 1
        for(int i = 1; i < lines.length; i++){
            String line = lines[i].trim();
            if(line.isEmpty()) continue;
            rules.put(i, line);
        }
        return rules;
    }

    public Map<Integer,String> fetchNumberedRules(PC pc) throws Exception {
        Session session = connectSSH.establishSSH(
            pc.getIpAddress(), pc.getPort(), pc.getPcUsername(), pc.getPassword());
        session.disconnect();
        String output = ubuntuInfo.executeCommand(
          session,
          "echo '"+pc.getPassword()+"' | sudo -S ufw status numbered"
        );
        session.disconnect();
      
        Map<Integer,String> map = new LinkedHashMap<>();
        String[] lines = output.split("\\r?\\n");
        Pattern p = Pattern.compile("^\\s*\\[(\\d+)\\]\\s*(.*)$");
        for(String line: lines) {
          Matcher m = p.matcher(line);
          if (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            map.put(idx, m.group(2).trim());
          }
        }
        return map;
      }
      

}