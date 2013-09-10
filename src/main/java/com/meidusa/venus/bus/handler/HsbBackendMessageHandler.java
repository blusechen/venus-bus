package com.meidusa.venus.bus.handler;

import com.meidusa.venus.bus.network.HsbBackendConnection;
import com.meidusa.venus.bus.network.HsbFrontendConnection;
import com.meidusa.venus.exception.VenusExceptionCodeConstant;
import com.meidusa.venus.io.packet.AbstractServicePacket;
import com.meidusa.venus.io.packet.AbstractVenusPacket;
import com.meidusa.venus.io.packet.ErrorPacket;
import com.meidusa.venus.io.packet.ServiceResponsePacket;
import com.meidusa.venus.io.packet.VenusRouterPacket;
import com.meidusa.venus.io.packet.VenusServiceHeaderPacket;
import com.meidusa.toolkit.net.MessageHandler;

public class HsbBackendMessageHandler implements MessageHandler<HsbBackendConnection> {
	private ClientConnectionObserver clientConnectionObserver;
	public ClientConnectionObserver getClientConnectionObserver() {
		return clientConnectionObserver;
	}

	public void setClientConnectionObserver(ClientConnectionObserver clientConnectionObserver) {
		this.clientConnectionObserver = clientConnectionObserver;
	}

	@Override
	public void handle(HsbBackendConnection conn, byte[] message) {
		int type = AbstractServicePacket.getType(message);
		if(type == AbstractVenusPacket.PACKET_TYPE_ROUTER){
			
			HsbFrontendConnection  clientConn = (HsbFrontendConnection)clientConnectionObserver.getConnection(VenusRouterPacket.getConnectionSequenceID(message));
			conn.removeRequest(VenusRouterPacket.getRemoteRequestID(message));
			byte[] response = VenusRouterPacket.getData(message);
			if(clientConn != null){
				if(clientConn.removeUnCompleted(VenusRouterPacket.getSourceRequestID(message))){
					clientConn.write(response);
				}/*else{
					
					VenusServiceHeaderPacket packet = new VenusServiceHeaderPacket();
					packet.init(response);
        			ErrorPacket error = new ErrorPacket();
        			AbstractServicePacket.copyHead(packet, error);
                	error.errorCode = VenusExceptionCodeConstant.SERVICE_RESPONSE_HEADER_ERROR_EXCEPTION;
                	error.message = "Service response header error";
                	clientConn.write(error.toByteBuffer());
				}*/
			}
		}
		
	}
}
