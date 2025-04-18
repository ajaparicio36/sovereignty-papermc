package com.tatayless.sovereignty.models;

import java.util.Date;

public class Trade {
    public enum Status {
        PENDING, COMPLETED, FAILED, CANCELLED
    }

    private String id;
    private String sendingNationId;
    private String receivingNationId;
    private Status status;
    private int consecutiveTrades;
    private Date lastExecution;
    private Date nextExecution;
    private int executionInterval; // in Minecraft days

    public Trade(String id, String sendingNationId, String receivingNationId) {
        this.id = id;
        this.sendingNationId = sendingNationId;
        this.receivingNationId = receivingNationId;
        this.status = Status.PENDING;
        this.consecutiveTrades = 0;
    }

    public String getId() {
        return id;
    }

    public String getSendingNationId() {
        return sendingNationId;
    }

    public String getReceivingNationId() {
        return receivingNationId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getConsecutiveTrades() {
        return consecutiveTrades;
    }

    public void setConsecutiveTrades(int consecutiveTrades) {
        this.consecutiveTrades = consecutiveTrades;
    }

    public void incrementConsecutiveTrades() {
        this.consecutiveTrades++;
    }

    public Date getLastExecution() {
        return lastExecution;
    }

    public void setLastExecution(Date lastExecution) {
        this.lastExecution = lastExecution;
    }

    public Date getNextExecution() {
        return nextExecution;
    }

    public void setNextExecution(Date nextExecution) {
        this.nextExecution = nextExecution;
    }

    public int getExecutionInterval() {
        return executionInterval;
    }

    public void setExecutionInterval(int executionInterval) {
        this.executionInterval = executionInterval;
    }

    public boolean isReady() {
        if (this.status != Status.PENDING) {
            return false;
        }
        
        if (nextExecution == null) {
            return false;
        }
        
        return new Date().after(nextExecution);
    }
}
