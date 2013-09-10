package com.meidusa.venus.bus.handler;

import java.util.List;

import org.apache.log4j.Logger;

import com.meidusa.venus.bus.network.HsbBackendConnection;
import com.meidusa.venus.bus.network.HsbFrontendConnection;
import com.meidusa.venus.exception.VenusExceptionCodeConstant;
import com.meidusa.venus.bus.ServiceRemoteManager;
import com.meidusa.venus.io.packet.AbstractServicePacket;
import com.meidusa.venus.io.packet.ErrorPacket;
import com.meidusa.venus.io.packet.PacketConstant;
import com.meidusa.venus.io.packet.PingPacket;
import com.meidusa.venus.io.packet.PongPacket;
import com.meidusa.venus.io.packet.ServiceAPIPacket;
import com.meidusa.venus.io.packet.ServicePacketBuffer;
import com.meidusa.venus.io.packet.VenusRouterPacket;
import com.meidusa.venus.io.packet.serialize.SerializeServiceRequestPacket;
import com.meidusa.venus.util.Range;
import com.meidusa.toolkit.common.util.Tuple;
import com.meidusa.toolkit.net.BackendConnectionPool;
import com.meidusa.toolkit.net.MessageHandler;
import com.meidusa.toolkit.net.util.InetAddressUtil;
import com.meidusa.toolkit.util.StringUtil;

public class HsbFrontendMessageHandler implements MessageHandler<HsbFrontendConnection> {
    private static Logger logger = Logger.getLogger(HsbFrontendMessageHandler.class);
	
    private ServiceRemoteManager remoteManager;
    
   
    
	public ServiceRemoteManager getRemoteManager() {
		return remoteManager;
	}

	
	public void setRemoteManager(ServiceRemoteManager remoteManager) {
		this.remoteManager = remoteManager;
	}
	
	//Map<String,List<Tuple<Range,ObjectPool>>> serviceMap = new HashMap<String,List<Tuple<Range,ObjectPool>>>();
	@Override
	public void handle(HsbFrontendConnection conn, final byte[] message) {
		int type = AbstractServicePacket.getType(message);
        switch (type) {
            case PacketConstant.PACKET_TYPE_PING:
                PingPacket ping = new PingPacket();
                ping.init(message);
                PongPacket pong = new PongPacket();
                AbstractServicePacket.copyHead(ping, pong);
                conn.write(pong.toByteBuffer());
                if (logger.isDebugEnabled()) {
                    logger.debug("receive ping packet from " + conn.getId());
                }
                break;
                
            //ignore this packet
            case PacketConstant.PACKET_TYPE_PONG:
            	
            	break;
            case PacketConstant.PACKET_TYPE_SERVICE_REQUEST:{
            	SerializeServiceRequestPacket request = null;
            	try{
            		VenusRouterPacket routerPacket = new VenusRouterPacket();
            		routerPacket.srcIP = InetAddressUtil.pack(conn.getInetAddress().getAddress());
            		routerPacket.data = message;
            		routerPacket.connectionID = conn.getSequenceID();
            		routerPacket.frontendRequestID = conn.getNextRequestID();
            		routerPacket.serializeType = conn.getSerializeType();
            		conn.addUnCompleted(routerPacket.frontendRequestID, routerPacket);
            		ServiceAPIPacket apiPacket = new ServiceAPIPacket();
            		try{
	            		ServicePacketBuffer packetBuffer = new ServicePacketBuffer(message);
	            		try{
	            			apiPacket.init(packetBuffer);
	            		}catch(Exception e){
	            			logger.error("decode error",e);
	            			ErrorPacket error = new ErrorPacket();
	            			AbstractServicePacket.copyHead(apiPacket, error);
		                	error.errorCode = VenusExceptionCodeConstant.PACKET_DECODE_EXCEPTION;
		                	error.message = "decode packet exception:"+e.getMessage();
		                	conn.write(error.toByteBuffer());
		                	return;
	            		}
	            		
		                final String apiName = apiPacket.apiName;
		                int index = apiName.lastIndexOf(".");
		                String serviceName = apiName.substring(0, index);
		                String methodName = apiName.substring(index + 1);
		                List<Tuple<Range,BackendConnectionPool>> list = remoteManager.getRemoteList(serviceName);
		                
		                //service not found
		                if(list == null || list.size() == 0){
		                	ErrorPacket error = new ErrorPacket();
		                	AbstractServicePacket.copyHead(apiPacket, error);
		                	error.errorCode = VenusExceptionCodeConstant.SERVICE_NOT_FOUND;
		                	error.message = "service not found :"+ serviceName;
		                	conn.write(error.toByteBuffer());
		                	return;
		                }
		                
		                
		                for(Tuple<Range,BackendConnectionPool> tuple : list){
		                	
		                	if(tuple.left.contains(apiPacket.serviceVersion)){
		                		HsbBackendConnection remoteConn = null;
		                		try{
		                			remoteConn = (HsbBackendConnection)tuple.right.borrowObject();
		                			routerPacket.backendRequestID = remoteConn.getNextRequestID();
		                			remoteConn.addRequest(routerPacket.backendRequestID, routerPacket.connectionID, routerPacket.frontendRequestID);
		                			remoteConn.write(routerPacket.toByteBuffer());
		                			return;
		                		}catch(Exception e){
		                			conn.getRetryHandler().addRetry(conn, routerPacket);
		                			return;
		                		}finally{
		                			if(remoteConn != null){
		                				tuple.right.returnObject(remoteConn);
		                			}
		                		}
		                	}
		                }
		                
		                //Service version not match
            			ErrorPacket error = new ErrorPacket();
            			AbstractServicePacket.copyHead(apiPacket, error);
	                	error.errorCode = VenusExceptionCodeConstant.SERVICE_VERSION_NOT_ALLOWD_EXCEPTION;
	                	error.message = "Service version not match";
	                	conn.write(error.toByteBuffer());
	
            		}catch(Exception e){
            			logger.error("decode error",e);
            			ErrorPacket error = new ErrorPacket();
            			AbstractServicePacket.copyHead(apiPacket, error);
	                	error.errorCode = VenusExceptionCodeConstant.PACKET_DECODE_EXCEPTION;
	                	error.message = "decode packet exception:"+e.getMessage();
	                	conn.write(error.toByteBuffer());
	                	return;
            		}
	                
            	}catch(Exception e){
            		ErrorPacket error = new ErrorPacket();
            		AbstractServicePacket.copyHead(request, error);
                	error.errorCode = VenusExceptionCodeConstant.SERVICE_UNAVAILABLE_EXCEPTION;
                	error.message = e.getMessage();
                	conn.write(error.toByteBuffer());
                	logger.error("error when invoke", e);
                	return;
            	}catch(Error e){
            		ErrorPacket error = new ErrorPacket();
            		AbstractServicePacket.copyHead(request, error);
                	error.errorCode = VenusExceptionCodeConstant.SERVICE_UNAVAILABLE_EXCEPTION;
                	error.message = e.getMessage();
                	conn.write(error.toByteBuffer());
                	logger.error("error when invoke", e);
                	return;
            	}
                break;
        	}
            case PacketConstant.AUTHEN_TYPE_PASSWORD:
            	
            	break;
            default:
                StringBuilder buffer = new StringBuilder("receive unknown type packet from ");
                buffer.append(conn.getId()).append("\n");
                buffer.append("-------------------------------").append("\n");
                buffer.append(StringUtil.dumpAsHex(message, message.length)).append("\n");
                buffer.append("-------------------------------").append("\n");
                logger.warn(buffer.toString());

        }
	}

}
