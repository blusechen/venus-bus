package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;

import com.meidusa.toolkit.net.FrontendConnection;
import com.meidusa.venus.bus.handler.RetryMessageHandler;
import com.meidusa.venus.io.network.VenusFrontendConnectionFactory;

/**
 * Bus前端连接工厂
 * @author structchen
 *
 */
public class BusFrontendConnectionFactory extends
		VenusFrontendConnectionFactory {

	private RetryMessageHandler retry;

	public RetryMessageHandler getRetry() {
		return retry;
	}

	public void setRetry(RetryMessageHandler retry) {
		this.retry = retry;
	}

	protected FrontendConnection getConnection(SocketChannel channel) {
		BusFrontendConnection conn = new BusFrontendConnection(channel);
		conn.setRequestHandler(getMessageHandler());
		conn.setAuthenticateProvider(getAuthenticateProvider());
		conn.setRetryHandler(retry);
		return conn;
	}
}
