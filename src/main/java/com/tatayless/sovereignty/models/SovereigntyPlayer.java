package com.tatayless.sovereignty.models;

import java.util.Date;
import java.util.UUID;

public class SovereigntyPlayer {
    private String id;
    private String name;
    private String nationId;
    private Nation.Role role;
    private int soldierLives;
    private Date createdAt;
    private Date updatedAt;

    public SovereigntyPlayer(String id, String name) {
        this.id = id;
        this.name = name;
        this.role = null;
        this.soldierLives = 0;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    public SovereigntyPlayer(UUID uuid, String name) {
        this(uuid.toString(), name);
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNationId() {
        return nationId;
    }

    public void setNationId(String nationId) {
        this.nationId = nationId;
    }

    public Nation.Role getRole() {
        return role;
    }

    public void setRole(Nation.Role role) {
        this.role = role;
    }

    public int getSoldierLives() {
        return soldierLives;
    }

    public void setSoldierLives(int soldierLives) {
        this.soldierLives = soldierLives;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isPresident() {
        return role == Nation.Role.PRESIDENT;
    }

    public boolean isSenator() {
        return role == Nation.Role.SENATOR;
    }

    public boolean isSoldier() {
        return role == Nation.Role.SOLDIER;
    }

    public boolean isCitizen() {
        return role == Nation.Role.CITIZEN;
    }

    public boolean isOfficer() {
        return role == Nation.Role.PRESIDENT || role == Nation.Role.SENATOR;
    }

    public boolean hasNation() {
        return nationId != null && !nationId.isEmpty();
    }
}
