package com.meidusa.venus.bus.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.meidusa.toolkit.net.MessageHandler;
import com.meidusa.toolkit.net.util.InetAddressUtil;
import com.meidusa.toolkit.util.TimeUtil;
import com.meidusa.venus.bus.network.BusBackendConnection;
import com.meidusa.venus.bus.network.BusFrontendConnection;
import com.meidusa.venus.bus.packet.SimpleServiceResponsePacket;
import com.meidusa.venus.bus.util.VenusTrafficCollector;
import com.meidusa.venus.io.packet.AbstractServicePacket;
import com.meidusa.venus.io.packet.AbstractVenusPacket;
import com.meidusa.venus.io.packet.VenusRouterPacket;
import com.meidusa.venus.util.UUID;

/**
 * 后端服务的 消息处理
 * 
 * @author structchen
 * 
 */
public class BusBackendMessageHandler implements MessageHandler<BusBackendConnection, byte[]> {
	final static Logger logger = LoggerFactory.getLogger("venus.backend.performance");
    private ClientConnectionObserver clientConnectionObserver;

    public ClientConnectionObserver getClientConnectionObserver() {
        return clientConnectionObserver;
    }

    public void setClientConnectionObserver(ClientConnectionObserver clientConnectionObserver) {
        this.clientConnectionObserver = clientConnectionObserver;
    }

    @Override
    public void handle(BusBackendConnection conn,final byte[] message) {
    	VenusTrafficCollector.getInstance().addInput(message.length);
        int type = AbstractServicePacket.getType(message);
        if (type == AbstractVenusPacket.PACKET_TYPE_ROUTER) {

            BusFrontendConnection clientConn = (BusFrontendConnection) clientConnectionObserver.getConnection(VenusRouterPacket
                    .getConnectionSequenceID(message));
            conn.removeRequest(VenusRouterPacket.getRemoteRequestID(message));
            byte[] response = VenusRouterPacket.getData(message);
            if (clientConn != null) {
            	VenusRouterPacket router = clientConn.removeUnCompleted(VenusRouterPacket.getSourceRequestID(message));
            	if(router != null){
            		if(logger.isDebugEnabled()){
            			long cost = (TimeUtil.currentTimeMillis()-router.startTime);
            			logger.debug("[{}] traceId={}, api={}, srcIP={}, destIP={}, closed={}",cost, router.traceId,router.api,InetAddressUtil.intToAddress(router.srcIP),conn.getHost(),clientConn.isClosed());
            			
            		}
            		if(!clientConn.isClosed()){
            			clientConn.write(response);
            		}
                }else{
                	VenusRouterPacket routerResp = new VenusRouterPacket();
                	routerResp.init(message);
                	SimpleServiceResponsePacket packet = new SimpleServiceResponsePacket();
                	packet.init(response);
        			logger.error("*abandoned* requestId={}, srcIP={}, destIP={}",packet.clientRequestId,InetAddressUtil.intToAddress(routerResp.srcIP),conn.getHost());
                }
            }else{
            	VenusRouterPacket routerResp = new VenusRouterPacket();
            	routerResp.init(message);
            	SimpleServiceResponsePacket packet = new SimpleServiceResponsePacket();
            	packet.init(response);
    			logger.error("*abandoned* requestId={}, srcIP={}, destIP={}",packet.clientRequestId,InetAddressUtil.intToAddress(routerResp.srcIP),conn.getHost());
            }
        }else if(type == AbstractVenusPacket.PACKET_TYPE_PONG){
        	conn.setLastPong(TimeUtil.currentTimeMillis());
        }

    }
}
