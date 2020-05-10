package server;

public enum ServerState {
    LISTEN, SYN_RECEIVED, ESTAB, CLOSE_WAIT, LAST_ACK, CLOSED
}
