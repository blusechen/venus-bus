package com.meidusa.venus.bus;

import java.io.IOException;

import com.meidusa.toolkit.net.ConnectionConnector;
import com.meidusa.toolkit.net.ConnectionManager;

public class VenusConnectionConnector extends ConnectionConnector {
	private int processorSize = Runtime.getRuntime().availableProcessors();
	public VenusConnectionConnector(String name) throws IOException {
		super(name);
	}

	
    public int getProcessorSize() {
		return processorSize;
	}


	public void setProcessorSize(int processorSize) {
		this.processorSize = processorSize;
	}


	public void initProcessors() throws IOException {
    	if(processors == null){
    		processors = new ConnectionManager[processorSize];
    		for(int i=0;i<processors.length ;i++){
    			try {
					processors[i] = new ConnectionManager(this.getName()+"-Manager-"+i, getExecutorSize());
					processors[i].start();
				} catch (IOException e) {
					LOGGER.error("create connection Manager error", e);
					this.shutdown();
				}
    		}
    	}
	}
}

