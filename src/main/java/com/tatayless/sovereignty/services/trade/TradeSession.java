package com.tatayless.sovereignty.services.trade;

/**
 * Represents a player's active trade session.
 * This class tracks the state and context of a player interacting with the
 * trade system.
 */
public class TradeSession {
    private final String nationId;
    private String tradeId;
    private TradeSessionType type;
    private String otherNationId;
    private int executionInterval = 24; // Default execution interval in hours

    /**
     * Creates a new trade session with a nation ID and session type
     *
     * @param nationId The ID of the nation this session belongs to
     * @param type     The type of session
     */
    public TradeSession(String nationId, TradeSessionType type) {
        this.nationId = nationId;
        this.type = type;
    }

    /**
     * Creates a new trade session with a nation ID and trade ID
     *
     * @param nationId The ID of the nation this session belongs to
     * @param tradeId  The ID of the trade being accessed
     * @param type     The type of session
     */
    public TradeSession(String nationId, String tradeId, TradeSessionType type) {
        this.nationId = nationId;
        this.tradeId = tradeId;
        this.type = type;
    }

    public String getNationId() {
        return nationId;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public TradeSessionType getType() {
        return type;
    }

    public void setType(TradeSessionType type) {
        this.type = type;
    }

    public String getOtherNationId() {
        return otherNationId;
    }

    public void setOtherNationId(String otherNationId) {
        this.otherNationId = otherNationId;
    }

    public int getExecutionInterval() {
        return executionInterval;
    }

    public void setExecutionInterval(int executionInterval) {
        this.executionInterval = executionInterval;
    }
}
