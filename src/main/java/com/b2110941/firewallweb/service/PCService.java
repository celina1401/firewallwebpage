/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.repository.pcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PCService {

    @Autowired
    private pcRepository pcRepository;

    /**
     * Retrieves all PCs from the repository.
     *
     * @return List of all PC entities
     */
    public List<PC> findAll() {
        return pcRepository.findAll();
    }

    /**
     * Retrieves a PC by its pcName.
     *
     * @param pcName The name of the PC
     * @return Optional containing the PC if found, or empty if not found
     */
    public Optional<PC> findByPcName(String pcName) {
        if (pcName == null || pcName.trim().isEmpty()) {
            throw new IllegalArgumentException("pcName cannot be null or empty");
        }
        return pcRepository.findBypcName(pcName);
    }

    /**
     * Retrieves a PC by its pcUsername.
     *
     * @param pcUsername The username associated with the PC
     * @return Optional containing the PC if found, or empty if not found
     */
    public Optional<PC> findByPcUsername(String pcUsername) {
        if (pcUsername == null || pcUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("pcUsername cannot be null or empty");
        }
        return pcRepository.findBypcUsername(pcUsername);
    }

    /**
     * Retrieves all PCs owned by a specific user.
     *
     * @param ownerUsername The username of the owner
     * @return List of PCs owned by the user
     */
    public List<PC> findByOwnerUsername(String ownerUsername) {
        if (ownerUsername == null || ownerUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("ownerUsername cannot be null or empty");
        }
        return pcRepository.findByOwnerUsername(ownerUsername);
    }

    /**
     * Retrieves a PC by its pcName and ownerUsername.
     *
     * @param pcName        The name of the PC
     * @param ownerUsername The username of the owner
     * @return Optional containing the PC if found, or empty if not found
     */
    public Optional<PC> findByPcNameAndOwnerUsername(String pcName, String ownerUsername) {
        if (pcName == null || pcName.trim().isEmpty()) {
            throw new IllegalArgumentException("pcName cannot be null or empty");
        }
        if (ownerUsername == null || ownerUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("ownerUsername cannot be null or empty");
        }
        return pcRepository.findByPcNameAndOwnerUsername(pcName, ownerUsername);
    }

    /**
     * Saves a PC entity to the repository.
     *
     * @param computer The PC to save
     * @return The saved PC entity
     * @throws IllegalArgumentException if required fields are invalid
     */
    public PC save(PC computer) {
        if (computer == null) {
            throw new IllegalArgumentException("PC cannot be null");
        }
        if (computer.getPcName() == null || computer.getPcName().trim().isEmpty()) {
            throw new IllegalArgumentException("pcName cannot be null or empty");
        }
        if (computer.getIpAddress() == null || computer.getIpAddress().trim().isEmpty()) {
            throw new IllegalArgumentException("ipAddress cannot be null or empty");
        }
        if (computer.getPcUsername() == null || computer.getPcUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("pcUsername cannot be null or empty");
        }
        if (computer.getOwnerUsername() == null || computer.getOwnerUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("ownerUsername cannot be null or empty");
        }
        return pcRepository.save(computer);
    }

    /**
     * Deletes a PC by its ID.
     *
     * @param id The ID of the PC to delete
     * @throws IllegalArgumentException if id is null or empty
     */
    public void deleteById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }
        pcRepository.deleteById(id);
    }
}