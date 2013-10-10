package com.meidusa.venus.bus;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import com.meidusa.toolkit.net.ConnectionManager;

/**
 * Spring factory bean,��ΪConnection Manager�Ĺ���,ÿ�� manager Ĭ�ϵ�ִ���߳�Ϊ CPU core ����
 * 
 * @author structchen
 *
 */
public class ConnectionManagerFactoryBean implements FactoryBean<ConnectionManager[]> ,InitializingBean {
	private static AtomicInteger index = new AtomicInteger();
	/**
	 * ÿ�� manager�����Ժ�,Ĭ��ִ���߳�����
	 */
	private int executorSize = Runtime.getRuntime().availableProcessors();
	
	/**
	 * Manager������ǰ׺
	 */
	private String prefix = "Manager";
	
	/**
	 * �������ٸ�Manager,Ĭ��ΪCPU Core����
	 */
	private int size = Runtime.getRuntime().availableProcessors();
	
	private ConnectionManager[] items;
	
	/**
	 * �Ƿ��ǵ���
	 */
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
		if(singleton){
			return items;
		}else{
			ConnectionManager[] items = new ConnectionManager[size];
			for(int i=0;i<size;i++){
				items[i] = new ConnectionManager(this.getPrefix()+"-"+index.getAndIncrement()+"-"+i, this.getExecutorSize());
				items[i].start();
			}
			return items;
		}
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
		if(singleton){
			items = new ConnectionManager[size];
			for(int i=0;i<size;i++){
				items[i] = new ConnectionManager(this.getPrefix()+"-"+i, this.getExecutorSize());
				items[i].start();
			}
		}
	}

}
