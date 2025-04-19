package com.tatayless.sovereignty.services.trade;

/**
 * Represents a player's active trade session.
 * This class tracks the state and context of a player interacting with the
 * trade system.
 */
public class TradeSession {
    private TradeSessionType type;
    private String nationId;
    private String partnerNationId;
    private int interval;
    private String tradeId;

    public TradeSession(TradeSessionType type, String nationId, String partnerNationId, int interval) {
        this.type = type;
        this.nationId = nationId;
        this.partnerNationId = partnerNationId;
        this.interval = interval;
    }

    // Used for sessions where we're working with an existing trade
    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public TradeSessionType getType() {
        return type;
    }

    public String getNationId() {
        return nationId;
    }

    public String getPartnerNationId() {
        return partnerNationId;
    }

    public int getInterval() {
        return interval;
    }

    public String getTradeId() {
        return tradeId;
    }
}
