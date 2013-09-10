package com.meidusa.venus.bus;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import com.meidusa.toolkit.net.ConnectionManager;

public class ConnectionManagerFactoryBean implements FactoryBean<ConnectionManager[]> ,InitializingBean {
	
	private int executorSize = Runtime.getRuntime().availableProcessors();
	private String prefix = "Manager";
	private int size = Runtime.getRuntime().availableProcessors();
	private ConnectionManager[] items;
	private boolean singleton = true;
	
	public void setSingleton(boolean singleton) {
		this.singleton = singleton;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public int getExecutorSize() {
		return executorSize;
	}

	public void setExecutorSize(int executorSize) {
		this.executorSize = executorSize;
	}

	@Override
	public ConnectionManager[] getObject() throws Exception {
		return items;
	}

	@Override
	public Class<?> getObjectType() {
		return ConnectionManager[].class;
	}

	@Override
	public boolean isSingleton() {
		return singleton;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		items = new ConnectionManager[size];
		for(int i=0;i<size;i++){
			items[i] = new ConnectionManager(this.getPrefix()+"-"+i, this.getExecutorSize());
			items[i].start();
		}
	}

}
