package com.meidusa.venus.bus.network;

import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.meidusa.toolkit.common.bean.util.Initialisable;
import com.meidusa.toolkit.common.bean.util.InitialisationException;
import com.meidusa.toolkit.net.BackendConnection;
import com.meidusa.venus.io.network.VenusBackendConnectionFactory;

/**
 * 后端连接工厂
 * 
 * @author structchen
 * 
 */
public class BusBackendConnectionFactory extends VenusBackendConnectionFactory implements Initialisable {
    private static Logger logger = LoggerFactory.getLogger(BusBackendConnectionFactory.class);

    @Override
    protected BackendConnection create(SocketChannel channel) {
        BusBackendConnection c = new BusBackendConnection(channel);
        c.setResponseMessageHandler(getMessageHandler());
        /*
         * if(Thread.currentThread() instanceof ConnectionManager){ ConnectionManager manager =
         * (ConnectionManager)Thread.currentThread(); c.setProcessor(manager); }
         */
        return c;
    }

    @Override
    public void init() throws InitialisationException {
        logger.info("backend socket receiveBuffer=" + this.getReceiveBufferSize() + "K, sentBuffer=" + this.getSendBufferSize() + "K");
    }
}
