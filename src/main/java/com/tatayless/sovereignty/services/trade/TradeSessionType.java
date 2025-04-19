package com.tatayless.sovereignty.services.trade;

/**
 * Enum representing different types of trade sessions.
 */
public enum TradeSessionType {
    CREATE, // Creating a new trade
    LIST, // Viewing trade list
    DELETE_SELECT, // Selecting a trade to delete
    DELETE_CONFIRM, // Confirming trade deletion
    NPC_CREATE, // Creating a trade NPC
    SENDING_VAULT, // Accessing the sending vault
    RECEIVING_VAULT // Accessing the receiving vault
}
