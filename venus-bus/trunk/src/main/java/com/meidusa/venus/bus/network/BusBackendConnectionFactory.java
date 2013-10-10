package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.meidusa.toolkit.net.BackendConnection;
import com.meidusa.venus.io.network.VenusBackendConnectionFactory;

/**
 * 后端连接工厂
 * @author structchen
 *
 */
public class BusBackendConnectionFactory extends VenusBackendConnectionFactory implements InitializingBean{
	private static Logger logger = LoggerFactory.getLogger(BusBackendConnectionFactory.class);
	@Override
	protected BackendConnection create(SocketChannel channel) {
		BusBackendConnection c = new BusBackendConnection(channel);
		c.setResponseMessageHandler(getMessageHandler());
        return c;
	}
	@Override
	public void afterPropertiesSet() throws Exception {
		logger.info("backend socket receiveBuffer="+this.getReceiveBufferSize()+"K, sentBuffer="+this.getSendBufferSize()+"K");
	}
	
	
}
