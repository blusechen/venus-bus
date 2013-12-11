package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.meidusa.venus.bus.handler.BusBackendMessageHandler;
import com.meidusa.venus.bus.handler.ClientConnectionObserver;
import com.meidusa.venus.io.network.VenusBackendConnection;
import com.meidusa.venus.io.utils.Bits;

/**
 * 负责Bus后端连接
 * 
 * @author structchen
 * 
 */
public class BusBackendConnection extends VenusBackendConnection {
    private AtomicLong requestSeq = new AtomicLong();
    private final Map<Long, byte[]> unCompeleted = new ConcurrentHashMap<Long, byte[]>();

    public BusBackendConnection(SocketChannel channel) {
        super(channel);
    }

    /**
     * 
     * @return
     */
    public long getNextRequestID() {
        return requestSeq.getAndIncrement();
    }

    public boolean addRequest(long backendRequestId, long frontendConnID, long frontendRequestID) {
        byte[] tm = new byte[16];
        Bits.putLong(tm, 0, frontendConnID);
        Bits.putLong(tm, 8, frontendRequestID);
        unCompeleted.put(backendRequestId, tm);
        if (isClosed.get()) {
            unCompeleted.remove(backendRequestId);
            return false;
        } else {

            return true;
        }
    }

    public boolean removeRequest(long requestID) {
        return unCompeleted.remove(requestID) != null;
    }

    public boolean close() {
        boolean closed = super.close();
        if (closed) {
            if (this.getHandler() instanceof BusBackendMessageHandler) {
                ClientConnectionObserver os = ((BusBackendMessageHandler) this.getHandler()).getClientConnectionObserver();

                Iterator<Entry<Long, byte[]>> it = unCompeleted.entrySet().iterator();
                while (it.hasNext()) {
                    Entry<Long, byte[]> item = it.next();
                    long frontendConnID = Bits.getLong(item.getValue(), 0);
                    long frontendRequestID = Bits.getLong(item.getValue(), 8);
                    BusFrontendConnection conn = (BusFrontendConnection) os.getConnection(frontendConnID);
                    conn.retryRequestById(frontendRequestID);
                }

            }
        }
        return closed;
    }
}
