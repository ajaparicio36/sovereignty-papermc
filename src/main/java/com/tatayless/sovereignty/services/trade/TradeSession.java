package com.tatayless.sovereignty.services.trade;

public class TradeSession {
    private final TradeSessionType type;
    private final String nationId;
    private final String partnerNationId;
    private final int interval;
    private String tradeId;

    public TradeSession(TradeSessionType type, String nationId, String partnerNationId, int interval) {
        this.type = type;
        this.nationId = nationId;
        this.partnerNationId = partnerNationId;
        this.interval = interval;
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

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }
}
