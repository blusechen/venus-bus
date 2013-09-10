package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.meidusa.venus.backend.network.VenusFrontendConnection;
import com.meidusa.venus.bus.handler.RetryMessageHandler;
import com.meidusa.venus.io.packet.VenusRouterPacket;

public class HsbFrontendConnection extends VenusFrontendConnection {
	private AtomicLong requestSeq = new AtomicLong();
	private RetryMessageHandler retryHandler;
	
	public HsbFrontendConnection(SocketChannel channel) {
		super(channel);
	}
	
	private Map<Long,VenusRouterPacket> unCompleted = new ConcurrentHashMap<Long, VenusRouterPacket>(); 
	
	public void addUnCompleted(long requestId,VenusRouterPacket data){
		unCompleted.put(requestId,data);
	}
	
	public boolean removeUnCompleted(long requestId){
		return unCompleted.remove(requestId) != null;
	}
	
	public long getNextRequestID(){
		return requestSeq.getAndIncrement();
	}
	
	
	public RetryMessageHandler getRetryHandler() {
		return retryHandler;
	}

	public void setRetryHandler(RetryMessageHandler retryHandler) {
		this.retryHandler = retryHandler;
	}

	public void retryRequestById(long requestID){
		if(!this.isClosed()){
			retryHandler.addRetry(this, unCompleted.get(requestID));
		}
	}
	
}
