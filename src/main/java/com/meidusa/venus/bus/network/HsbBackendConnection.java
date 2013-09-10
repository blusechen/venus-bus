package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.meidusa.venus.bus.handler.ClientConnectionObserver;
import com.meidusa.venus.bus.handler.HsbBackendMessageHandler;
import com.meidusa.venus.client.net.VenusBackendConnection;
import com.meidusa.venus.io.utils.Bits;

public class HsbBackendConnection extends VenusBackendConnection {
	private AtomicLong requestSeq = new AtomicLong();
	private final Map<Long,byte[]> unCompeleted = new ConcurrentHashMap<Long,byte[]>();
	
	public HsbBackendConnection(SocketChannel channel) {
		super(channel);
	}
	
	public long getNextRequestID(){
		return requestSeq.getAndIncrement();
	}

	public boolean addRequest(long backendRequestId,long frontendConnID,long frontendRequestID){
		byte[] tm = new byte[16];
		Bits.putLong(tm, 0, frontendConnID);
		Bits.putLong(tm, 8, frontendRequestID);
		unCompeleted.put(backendRequestId,tm);
		if (isClosed.get()) {
			unCompeleted.remove(backendRequestId);
            return false;
        }else{
        	
        	return true;
        }
	}
	
	public boolean removeRequest(long requestID){
		return unCompeleted.remove(requestID) != null;
	}
	
	public boolean close() {
        boolean closed = super.close();
        if (closed) {
        	if(this.getHandler() instanceof HsbBackendMessageHandler){
        		ClientConnectionObserver os = ((HsbBackendMessageHandler)this.getHandler()).getClientConnectionObserver();
        		
        		Iterator<Entry<Long,byte[]>> it =unCompeleted.entrySet().iterator();
        		while(it.hasNext()){
        			Entry<Long,byte[]> item = it.next();
        			long frontendConnID = Bits.getLong(item.getValue(), 0);
        			long frontendRequestID = Bits.getLong(item.getValue(), 8);
        			HsbFrontendConnection conn = (HsbFrontendConnection)os.getConnection(frontendConnID);
        			conn.retryRequestById(frontendRequestID);
        		}
        		
        	}
        }
        return closed;
    }
}
