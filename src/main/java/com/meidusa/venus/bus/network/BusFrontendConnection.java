package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import com.meidusa.venus.bus.handler.RetryMessageHandler;
import com.meidusa.venus.io.network.VenusFrontendConnection;
import com.meidusa.venus.io.packet.VenusRouterPacket;

/**
 * 负责Bus前端连接
 * 
 * @author structchen
 * 
 */
public class BusFrontendConnection extends VenusFrontendConnection {
    private long requestSeq = 0L;
    private RetryMessageHandler retryHandler;

    public BusFrontendConnection(SocketChannel channel) {
        super(channel);
    }

    private Map<Long, VenusRouterPacket> unCompleted = new HashMap<Long, VenusRouterPacket>(16);

    public void addUnCompleted(long requestId, VenusRouterPacket data) {
        unCompleted.put(requestId, data);
    }

    public boolean removeUnCompleted(long requestId) {
        return unCompleted.remove(requestId) != null;
    }

    public long getNextRequestID() {
        return requestSeq++;
    }

    public RetryMessageHandler getRetryHandler() {
        return retryHandler;
    }

    public void setRetryHandler(RetryMessageHandler retryHandler) {
        this.retryHandler = retryHandler;
    }

    public void retryRequestById(long requestID) {
        if (!this.isClosed()) {
            retryHandler.addRetry(this, unCompleted.get(requestID));
        }
    }

}
