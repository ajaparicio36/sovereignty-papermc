package com.tatayless.sovereignty.models;

import java.util.*;

public class Nation {
    private String id;
    private String name;
    private double power;
    private int powerLevel;
    private boolean adminSetPower;
    private Set<ChunkLocation> claimedChunks;
    private Set<ChunkLocation> annexedChunks;
    private Set<String> alliances;
    private Set<String> wars;
    private String presidentId;
    private Set<String> senators;
    private Set<String> soldiers;
    private Set<String> citizens;
    private Date createdAt;
    private Date updatedAt;

    public Nation(String id, String name) {
        this.id = id;
        this.name = name;
        this.power = 1.0;
        this.powerLevel = 1;
        this.adminSetPower = false;
        this.claimedChunks = new HashSet<>();
        this.annexedChunks = new HashSet<>();
        this.alliances = new HashSet<>();
        this.wars = new HashSet<>();
        this.senators = new HashSet<>();
        this.soldiers = new HashSet<>();
        this.citizens = new HashSet<>();
        this.createdAt = new Date();
        this.updatedAt = new Date();
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

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        this.power = power;
        updatePowerLevel();
    }

    public void setPowerByAdmin(double power) {
        this.power = power;
        this.adminSetPower = true;
        updatePowerLevel();
    }

    public boolean isAdminSetPower() {
        return adminSetPower;
    }

    public void setAdminSetPower(boolean adminSetPower) {
        this.adminSetPower = adminSetPower;
    }

    public void addPower(double amount) {
        this.power += amount;
        updatePowerLevel();
    }

    public int getPowerLevel() {
        return powerLevel;
    }

    // Add method to explicitly set power level
    public void setPowerLevel(int powerLevel) {
        this.powerLevel = powerLevel;
    }

    public Set<ChunkLocation> getClaimedChunks() {
        return Collections.unmodifiableSet(claimedChunks);
    }

    public boolean addClaimedChunk(ChunkLocation chunk) {
        return claimedChunks.add(chunk);
    }

    public boolean removeClaimedChunk(ChunkLocation chunk) {
        return claimedChunks.remove(chunk);
    }

    public Set<ChunkLocation> getAnnexedChunks() {
        return Collections.unmodifiableSet(annexedChunks);
    }

    public boolean addAnnexedChunk(ChunkLocation chunk) {
        return annexedChunks.add(chunk);
    }

    public boolean removeAnnexedChunk(ChunkLocation chunk) {
        return annexedChunks.remove(chunk);
    }

    public Set<String> getAlliances() {
        return Collections.unmodifiableSet(alliances);
    }

    public boolean addAlliance(String nationId) {
        return alliances.add(nationId);
    }

    public boolean removeAlliance(String nationId) {
        return alliances.remove(nationId);
    }

    public Set<String> getWars() {
        return Collections.unmodifiableSet(wars);
    }

    public boolean addWar(String warId) {
        return wars.add(warId);
    }

    public boolean removeWar(String warId) {
        return wars.remove(warId);
    }

    public String getPresidentId() {
        return presidentId;
    }

    public void setPresidentId(String presidentId) {
        this.presidentId = presidentId;
    }

    public Set<String> getSenators() {
        return Collections.unmodifiableSet(senators);
    }

    public boolean addSenator(String playerId) {
        return senators.add(playerId);
    }

    public boolean removeSenator(String playerId) {
        return senators.remove(playerId);
    }

    public Set<String> getSoldiers() {
        return Collections.unmodifiableSet(soldiers);
    }

    public boolean addSoldier(String playerId) {
        return soldiers.add(playerId);
    }

    public boolean removeSoldier(String playerId) {
        return soldiers.remove(playerId);
    }

    public Set<String> getCitizens() {
        return Collections.unmodifiableSet(citizens);
    }

    public boolean addCitizen(String playerId) {
        return citizens.add(playerId);
    }

    public boolean removeCitizen(String playerId) {
        return citizens.remove(playerId);
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

    public boolean hasRole(String playerId, Role role) {
        switch (role) {
            case PRESIDENT:
                return playerId.equals(presidentId);
            case SENATOR:
                return senators.contains(playerId);
            case SOLDIER:
                return soldiers.contains(playerId);
            case CITIZEN:
                return citizens.contains(playerId);
            default:
                return false;
        }
    }

    public boolean isOfficer(String playerId) {
        return playerId.equals(presidentId) || senators.contains(playerId);
    }

    public boolean isMember(String playerId) {
        return playerId.equals(presidentId) || senators.contains(playerId) ||
                soldiers.contains(playerId) || citizens.contains(playerId);
    }

    // Helper method to recalculate power level based on power
    private void updatePowerLevel() {
        if (power >= 6.0) {
            powerLevel = 6;
        } else if (power >= 5.0) {
            powerLevel = 5;
        } else if (power >= 4.0) {
            powerLevel = 4;
        } else if (power >= 3.0) {
            powerLevel = 3;
        } else if (power >= 2.0) {
            powerLevel = 2;
        } else {
            powerLevel = 1;
        }
    }

    public enum Role {
        PRESIDENT,
        SENATOR,
        SOLDIER,
        CITIZEN
    }
}
