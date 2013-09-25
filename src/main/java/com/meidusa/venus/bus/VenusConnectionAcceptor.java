package com.meidusa.venus.bus;

import java.io.IOException;

import com.meidusa.toolkit.net.ConnectionAcceptor;
import com.meidusa.toolkit.net.ConnectionManager;
import com.meidusa.toolkit.net.ConnectionObserver;

/**
 * Venus �ض��� Acceptor,���� Observer
 * @author structchen
 *
 */
public class VenusConnectionAcceptor extends ConnectionAcceptor {
	private ConnectionObserver observer;
	
	public ConnectionObserver getObserver() {
		return observer;
	}

	public void setObserver(ConnectionObserver observer) {
		this.observer = observer;
	}

	public void initProcessors() throws IOException {
		super.initProcessors();
		if (observer != null) {
			for (int i = 0; i < processors.length; i++) {
				processors[i].addConnectionObserver(observer);
			}
		}
	}
}
