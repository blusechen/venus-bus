package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.meidusa.toolkit.net.BackendConnection;
import com.meidusa.venus.client.net.VenusBackendConnection;
import com.meidusa.venus.client.net.VenusBackendConnectionFactory;
import com.meidusa.venus.io.utils.Bits;

public class HsbBackendConnectionFactory extends VenusBackendConnectionFactory {
	
	@Override
	protected BackendConnection create(SocketChannel channel) {
		HsbBackendConnection c = new HsbBackendConnection(channel);
		c.setResponseMessageHandler(getMessageHandler());
        return c;
	}
	
}
