package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;

import com.meidusa.toolkit.net.BackendConnection;
import com.meidusa.venus.io.network.VenusBackendConnectionFactory;

/**
 * ������ӹ���
 * @author structchen
 *
 */
public class BusBackendConnectionFactory extends VenusBackendConnectionFactory {
	
	@Override
	protected BackendConnection create(SocketChannel channel) {
		BusBackendConnection c = new BusBackendConnection(channel);
		c.setResponseMessageHandler(getMessageHandler());
        return c;
	}
	
}
