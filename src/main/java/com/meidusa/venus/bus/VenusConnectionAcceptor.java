package com.meidusa.venus.bus;

import java.io.IOException;

import com.meidusa.toolkit.net.ConnectionAcceptor;
import com.meidusa.toolkit.net.ConnectionManager;
import com.meidusa.toolkit.net.ConnectionObserver;

public class VenusConnectionAcceptor extends ConnectionAcceptor {
	private ConnectionObserver observer;
	private int processorSize = Runtime.getRuntime().availableProcessors();

	public int getProcessorSize() {
		return processorSize;
	}


	public void setProcessorSize(int processorSize) {
		this.processorSize = processorSize;
	}
	
	public ConnectionObserver getObserver() {
		return observer;
	}

	public void setObserver(ConnectionObserver observer) {
		this.observer = observer;
	}

	public void initProcessors() throws IOException {
		if(processors == null){
        	processors = new ConnectionManager[processorSize];
        	for(int i=0;i<processors.length;i++){
        		processors[i] = new ConnectionManager(this.getName()+"-Manager-"+i,getExecutorSize());
        		processors[i].start();
        	}
        }
		if (observer != null) {
			for (int i = 0; i < processors.length; i++) {
				processors[i].addConnectionObserver(observer);
			}
		}
	}
}
