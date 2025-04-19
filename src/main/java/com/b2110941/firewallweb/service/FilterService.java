package com.b2110941.firewallweb.service;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FilterService {
    public String dateFormat(LocalDate date) throws ParseException {
        if (date == null)
            return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        System.out.println(formatter);
        return date.format(formatter);
    }

    public List<Map<String, String>> filterLogs(
            List<Map<String, String>> logs,
            LocalDate logDate,
            String action,
            String protocol,
            int rows) {
        DateTimeFormatter logDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return logs.stream()
                .filter(log -> {
                    // Lọc theo ngày nếu có
                    if (logDate != null) {
                        String dateStr = log.get("date");
                        if (dateStr == null || !dateStr.startsWith(logDate.format(logDateFormatter))) {
                            return false;
                        }
                    }

                    // Lọc theo hành động (ALLOW, DENY, ...)
                    if (!"all".equalsIgnoreCase(action)) {
                        String logAction = log.get("action");
                        if (logAction == null || !logAction.equalsIgnoreCase(action)) {
                            return false;
                        }
                    }

                    // Lọc theo protocol (TCP, UDP, ...)
                    if (!"all".equalsIgnoreCase(protocol)) {
                        String logProtocol = log.get("protocol");
                        if (logProtocol == null || !logProtocol.equalsIgnoreCase(protocol)) {
                            return false;
                        }
                    }

                    return true;
                })
                .limit(rows)
                .collect(Collectors.toList());
    }
}
