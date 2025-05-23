/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pc")
public class PC {
    @Id
    private String pcId;
    private String pcName;
    private String pcUsername;
    private String ipAddress;
    private int port;
    private String password;
    private String ownerUsername;
    private boolean sshStatus;
    // Add this field to your PC class
    private String ufwStatus;
    
    // Add getter and setter
    public String getUfwStatus() {
        return ufwStatus;
    }
    
    public void setUfwStatus(String ufwStatus) {
        this.ufwStatus = ufwStatus;
    }
    
    public PC() {   
    }

    public PC(String pcId, String pcName, String pcUsername, String ipAddress, int port, String password, String ownerUsername) {
        this.pcId = pcId;
        this.pcName = pcName;
        this.pcUsername = pcUsername;
        this.ipAddress = ipAddress;
        this.port = port;
        this.password = password;
        this.ownerUsername = ownerUsername.toLowerCase();
    } 

    public PC(String pcName, String pcUsername, String ipAddress, int port, String password, String ownerUsername) {
        this.pcName = pcName;
        this.pcUsername = pcUsername;
        this.ipAddress = ipAddress;
        this.port = port;
        this.password = password;
        this.ownerUsername = ownerUsername.toLowerCase();
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

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
      
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public boolean isSshStatus() { // Thay đổi tên getter theo chuẩn Java
        return sshStatus;
    }

    public void setSshStatus(boolean sshStatus) {
        this.sshStatus = sshStatus;
    }

    
}
