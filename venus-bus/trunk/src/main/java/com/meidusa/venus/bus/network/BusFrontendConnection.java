package com.meidusa.venus.bus.network;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.meidusa.toolkit.util.TimeUtil;
import com.meidusa.venus.bus.handler.RetryMessageHandler;
import com.meidusa.venus.bus.util.VenusTrafficCollector;
import com.meidusa.venus.io.network.VenusFrontendConnection;
import com.meidusa.venus.io.packet.ServiceAPIPacket;
import com.meidusa.venus.io.packet.VenusRouterPacket;
import com.meidusa.venus.util.UUID;

/**
 * 负责Bus前端连接
 * 
 * @author structchen
 * 
 */
public class BusFrontendConnection extends VenusFrontendConnection {
	final static Logger logger = LoggerFactory.getLogger("venus.backend.performance");
    private long requestSeq = 0L;
    private RetryMessageHandler retryHandler;
    private Map<Long, VenusRouterPacket> unCompleted = new HashMap<Long, VenusRouterPacket>();

    public BusFrontendConnection(SocketChannel channel) {
        super(channel);
    }
    
    public void addUnCompleted(long requestId, VenusRouterPacket data) {
        unCompleted.put(requestId, data);
    }

    public VenusRouterPacket removeUnCompleted(long requestId) {
        return unCompleted.remove(requestId);
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
    
    public void write(ByteBuffer buffer){
    	VenusTrafficCollector.getInstance().addOutput(buffer.remaining());
		super.write(buffer);
	}

    public boolean close() {
    	boolean isClosed = super.close();
    	if(isClosed){
    		Map<Long, VenusRouterPacket> tmp = new HashMap<Long, VenusRouterPacket>();
    		tmp.putAll(unCompleted);
    		unCompleted.clear();
    		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    		for(Map.Entry<Long, VenusRouterPacket> entry: tmp.entrySet()){
    			ServiceAPIPacket request = new ServiceAPIPacket();
    			request.init(entry.getValue().data);
    			long cost = (TimeUtil.currentTimeMillis()-entry.getValue().startTime);
    			logger.error("[{}] uncompleted=true, requestId={}, traceId={}, api={}, client={}, startTime={}",cost,request.clientRequestId,new UUID(request.traceId),request.apiName,this.host,format.format(new Date(entry.getValue().startTime)));
    		}
    	}
    	return isClosed;
    }
}
