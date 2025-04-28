package com.b2110941.firewallweb.service;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class FilterService {

    // public List<Map<String, String>> filterLogs(
    //         List<Map<String, String>> logs,
    //         // LocalDate logDate,
    //         String action,
    //         String protocol,
    //         int rows) {

    //     return logs.stream()
    //             .filter(log -> {                    

    //                 // Lọc theo hành động (ALLOW, DENY, ...)
    //                 if (!"all".equalsIgnoreCase(action)) {
    //                     String logAction = log.get("action");
    //                     if (logAction == null || !logAction.equalsIgnoreCase(action)) {
    //                         return false;
    //                     }
    //                 }

    //                 // Lọc theo protocol (TCP, UDP, ...)
    //                 if (!"all".equalsIgnoreCase(protocol)) {
    //                     String logProtocol = log.get("protocol");
    //                     if (logProtocol == null || !logProtocol.equalsIgnoreCase(protocol)) {
    //                         return false;
    //                     }
    //                 }

    //                 return true;
    //             })
    //             .limit(rows)
    //             .collect(Collectors.toList());
    // }

    public List<Map<String, String>> filterLogs(
            List<Map<String, String>> logs,
            String action,
            String protocol,
            int rows) {

        // 1. Lọc và giới hạn
        List<Map<String, String>> filtered = logs.stream()
                .filter(log -> {
                    // Lọc theo action
                    if (!"all".equalsIgnoreCase(action)) {
                        String logAction = log.get("action");
                        if (logAction == null || !logAction.equalsIgnoreCase(action)) {
                            return false;
                        }
                    }
                    // Lọc theo protocol
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

        // 2. Gán ID đồng bộ với front-end (bắt đầu từ 1)
        AtomicInteger counter = new AtomicInteger(1);
        filtered.forEach(log -> log.put("id", String.valueOf(counter.getAndIncrement())));

        return filtered;
    }
}
