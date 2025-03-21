package com.b2110941.firewallweb.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "pc_account")
public class PCAccount {
    @Id
    private String pcId;
    private String pcUsername;
    private String pcPassword;

    public PCAccount() {
    }
    
    public PCAccount(String pcId, String pcUsername, String pcPassword) {
        this.pcId = pcId;
        this.pcUsername = pcUsername;
        this.pcPassword = pcPassword;
    }

    public PCAccount(String pcUsername, String pcPassword) {
        this.pcUsername = pcUsername;
        this.pcPassword = pcPassword;
    }
    
    public String getPcId() {
        return pcId;
    }

    public void setPcId(String pcId) {
        this.pcId = pcId;
    }

    public String getPcUsername() {
        return pcUsername;
    }

    public void setPcUsername(String pcUsername) {
        this.pcUsername = pcUsername;
    }

    public String getPcPassword() {
        return pcPassword;
    }

    public void setPcPassword(String pcPassword) {
        this.pcPassword = pcPassword;
    }
    
    
    

}
