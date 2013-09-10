package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;

import com.meidusa.toolkit.net.FrontendConnection;
import com.meidusa.venus.backend.network.VenusFrontendConnectionFactory;
import com.meidusa.venus.bus.handler.RetryMessageHandler;

public class HsbFrontendConnectionFactory extends
		VenusFrontendConnectionFactory {

	private RetryMessageHandler retry;

	public RetryMessageHandler getRetry() {
		return retry;
	}

	public void setRetry(RetryMessageHandler retry) {
		this.retry = retry;
	}

	protected FrontendConnection getConnection(SocketChannel channel) {
		HsbFrontendConnection conn = new HsbFrontendConnection(channel);
		conn.setRequestHandler(getMessageHandler());
		conn.setAuthenticateProvider(getAuthenticateProvider());
		conn.setRetryHandler(retry);
		return conn;
	}
}
