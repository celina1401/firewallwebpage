/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.entity;

import org.springframework.data.annotation.Id;


public class PC {
    @Id
    private String pcId;
    private String pcName;
    private String pcUsername;
    private String ipAddress;
    private String port;
    private String password;

    public PC() {   
    }

    public PC(String pcId, String pcName, String pcUsername, String ipAddress, String port, String password) {
        this.pcId = pcId;
        this.pcName = pcName;
        this.pcUsername = pcUsername;
        this.ipAddress = ipAddress;
        this.port = port;
        this.password = password;
    }

    public PC(String pcId, String pcUsername, String ipAddress, String port, String password) {
        this.pcId = pcId;
        this.pcUsername = pcUsername;
        this.ipAddress = ipAddress;
        this.port = port;
        this.password = password;
    }


    public String getPcId() {
        return pcId;
    }

    public void setPcId(String pcId) {
        this.pcId = pcId;
    }

    public String getPcName() {
        return pcName;
    }

    public void setPcName(String pcName) {
        this.pcName = pcName;
    }

    public String getPcUsername() {
        return pcUsername;
    }

    public void setPcUsername(String pcUsername) {
        this.pcUsername = pcUsername;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }
      
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
