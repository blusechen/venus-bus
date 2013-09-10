package com.meidusa.venus.bus;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import com.meidusa.venus.bus.config.bean.HsbVenusConfig;
import com.meidusa.venus.bus.config.bean.RemoteServiceConfig;
import com.meidusa.venus.bus.network.HsbBackendConnectionFactory;
import com.meidusa.venus.client.xml.bean.FactoryConfig;
import com.meidusa.venus.client.xml.bean.PoolConfig;
import com.meidusa.venus.client.xml.bean.Remote;
import com.meidusa.venus.util.ArrayRange;
import com.meidusa.venus.util.BetweenRange;
import com.meidusa.venus.util.DefaultRange;
import com.meidusa.venus.util.Range;
import com.meidusa.venus.util.VenusBeanUtilsBean;
import com.meidusa.toolkit.common.bean.BeanContext;
import com.meidusa.toolkit.common.bean.BeanContextBean;
import com.meidusa.toolkit.common.bean.config.ConfigUtil;
import com.meidusa.toolkit.common.bean.config.ConfigurationException;
import com.meidusa.toolkit.common.bean.util.Initialisable;
import com.meidusa.toolkit.common.bean.util.InitialisationException;
import com.meidusa.toolkit.common.heartbeat.HeartbeatDelayed;
import com.meidusa.toolkit.common.heartbeat.HeartbeatManager;
import com.meidusa.toolkit.common.heartbeat.Status;
import com.meidusa.toolkit.common.poolable.MultipleLoadBalanceObjectPool;
import com.meidusa.toolkit.common.util.Tuple;
import com.meidusa.toolkit.net.BackendConnectionPool;
import com.meidusa.toolkit.net.ConnectionConnector;
import com.meidusa.toolkit.net.MessageHandler;
import com.meidusa.toolkit.net.MultipleLoadBalanceBackendConnectionPool;
import com.meidusa.toolkit.net.PollingBackendConnectionPool;
import com.meidusa.toolkit.util.StringUtil;

public abstract class AbstractServiceRemoteManager implements ServiceRemoteManager ,Initialisable ,BeanFactoryAware{
	private int defaultPoolSize = 8;
	private BeanContext beanContext;
	private BeanFactory beanFactory;
	private MessageHandler messageHandler;
	private Map<String,List<Tuple<Range, BackendConnectionPool>>> serviceMap = new HashMap<String,List<Tuple<Range, BackendConnectionPool>>>();
	private ConnectionConnector connector;
	private Map<String,BackendConnectionPool> poolMap = new HashMap<String,BackendConnectionPool>();
	private Map<String,BackendConnectionPool> realPoolMap = new HashMap<String,BackendConnectionPool>();
	public ConnectionConnector getConnector() {
		return connector;
	}

	public void setConnector(ConnectionConnector connectionManager) {
		this.connector = connectionManager;
	}

	public MessageHandler getMessageHandler() {
		return messageHandler;
	}

	public void setMessageHandler(MessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}

	public int getDefaultPoolSize() {
		return defaultPoolSize;
	}

	public void setDefaultPoolSize(int defaultPoolSize) {
		this.defaultPoolSize = defaultPoolSize;
	}

	@Override
	public List<Tuple<Range, BackendConnectionPool>> getRemoteList(String serviceName) {
		return serviceMap.get(serviceName);
	}
	
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory; 
	}
	public void init() throws InitialisationException {
		beanContext = new BeanContext(){
			public Object getBean(String beanName) {
				if(beanFactory != null){
					return beanFactory.getBean(beanName);
				}else{
					return null;
				}
			}
			
			public Object createBean(Class clazz) throws Exception {
				if(beanFactory instanceof AutowireCapableBeanFactory){
					AutowireCapableBeanFactory factory = (AutowireCapableBeanFactory)beanFactory;
					return factory.autowire(clazz, AutowireCapableBeanFactory.AUTOWIRE_BY_NAME, false);
				}
				return null;
			}
		};
		BeanContextBean.getInstance().setBeanContext(beanContext);
		
		VenusBeanUtilsBean.setInstance(new BeanUtilsBean(new ConvertUtilsBean(), new PropertyUtilsBean()) {
			@SuppressWarnings("unchecked")
			public void setProperty(Object bean, String name, Object value) throws IllegalAccessException,
					InvocationTargetException {
				if (value instanceof String) {
					PropertyDescriptor descriptor = null;
					try {
						descriptor = getPropertyUtils().getPropertyDescriptor(bean, name);
						if (descriptor == null) {
							return; // Skip this property setter
						} else {
							if (descriptor.getPropertyType().isEnum()) {
								Class<Enum> clazz = (Class<Enum>) descriptor.getPropertyType();
								value = Enum.valueOf(clazz, (String) value);
							} else {
								Object temp = null;
								try {
									temp = ConfigUtil.filter((String) value, beanContext);
								} catch (Exception e) {
								}
								if (temp == null) {
									temp = ConfigUtil.filter((String) value);
								}
								value = temp;
							}
						}
					} catch (NoSuchMethodException e) {
						return; // Skip this property setter
					}
				}
				super.setProperty(bean, name, value);
			}

		});
		
		try {
			this.serviceMap = load();
		} catch (Exception e) {
			throw new InitialisationException("init remote service Manager error",e);
		}
	}
	
	private Map<String,BackendConnectionPool> initRemoteMap(Map<String,Remote> remots) throws  Exception{
		Map<String,BackendConnectionPool> poolMap = new HashMap<String,BackendConnectionPool>();
		for(Map.Entry<String, Remote> entry: remots.entrySet()){
			Remote remote = entry.getValue();
			FactoryConfig factoryConfig = null;
			PoolConfig poolConfig = null;
			factoryConfig = remote.getFactory();
			poolConfig = remote.getPool();
			
			String ipAddress = factoryConfig != null ?factoryConfig.getIpAddressList(): factoryConfig.getIpAddressList();
			if(!StringUtil.isEmpty(ipAddress)){
				String ipList[] = StringUtil.split(ipAddress ,", ");
				
				BackendConnectionPool nioPools[] = new PollingBackendConnectionPool[ipList.length];
				
				for(int i=0;i<ipList.length;i++){
					HsbBackendConnectionFactory nioFactory = new HsbBackendConnectionFactory();
					if(realPoolMap.get(ipList[i]) != null){
						nioPools[i] = realPoolMap.get(ipList[i]);
						continue;
					}
					
					if(factoryConfig != null){
						BeanUtils.copyProperties(nioFactory, factoryConfig);
					}
					
					String temp[] = StringUtil.split(ipList[i],":");
					if(temp.length>1){
						nioFactory.setHost(temp[0]);
						nioFactory.setPort(Integer.valueOf(temp[1]));
					}else{
						nioFactory.setHost(temp[0]);
						nioFactory.setPort(16800);
					}
					
					if(remote.getAuthenticator() != null){
						nioFactory.setAuthenticator(remote.getAuthenticator());
					}
					
					nioFactory.setConnector(this.getConnector());
					nioFactory.setMessageHandler(getMessageHandler());
					
					nioPools[i] = new PollingBackendConnectionPool(ipList[i],nioFactory,poolConfig.getMaxActive());
					if(poolConfig != null){
						BeanUtils.copyProperties(nioPools[i], poolConfig);
					}
					nioPools[i].init();
				}
				String poolName = remote.getName();
				
				MultipleLoadBalanceBackendConnectionPool nioPool = new MultipleLoadBalanceBackendConnectionPool(poolName,MultipleLoadBalanceObjectPool.LOADBALANCING_ROUNDROBIN,nioPools);
				
				nioPool.init();
				poolMap.put(remote.getName(),nioPool);
				
			}
		}
		
		return poolMap;
	}
	
	private BackendConnectionPool createPoolFromAddressList(String ipAddressList){
		String ipList[] = StringUtil.split(ipAddressList ,", ");
		BackendConnectionPool nioPools[] = new PollingBackendConnectionPool[ipList.length];
		BackendConnectionPool pool = null;
		for(int i=0;i<ipList.length;i++){
			HsbBackendConnectionFactory nioFactory = new HsbBackendConnectionFactory();
			if(realPoolMap.get(ipList[i]) != null){
				nioPools[i] = realPoolMap.get(ipList[i]);
				continue;
			}
			
			String temp[] = StringUtil.split(ipList[i],":");
			if(temp.length>1){
				nioFactory.setHost(temp[0]);
				nioFactory.setPort(Integer.valueOf(temp[1]));
			}else{
				nioFactory.setHost(temp[0]);
				nioFactory.setPort(16800);
			}
			
			nioFactory.setConnector(this.getConnector());
			nioFactory.setMessageHandler(getMessageHandler());
			
			nioPools[i] = new PollingBackendConnectionPool(ipList[i],nioFactory,defaultPoolSize);
			nioPools[i].init();
			realPoolMap.put(ipList[i], nioPools[i]);
		}
		
		String poolName = "pool-"+ipAddressList;
		
		pool = new MultipleLoadBalanceBackendConnectionPool(poolName,MultipleLoadBalanceObjectPool.LOADBALANCING_ROUNDROBIN,nioPools);
		pool.init();
		
		return pool;
	}
	
	protected void reload() throws Exception{
		
		Map<String,List<Tuple<Range, BackendConnectionPool>>> oldServiceMap = this.serviceMap;
		this.serviceMap = this.load();
		
		if(oldServiceMap.size()>0){
			Collection<List<Tuple<Range, BackendConnectionPool>>> coll = oldServiceMap.values();
			for(Iterator<List<Tuple<Range, BackendConnectionPool>>> it = coll.iterator();it.hasNext();){
				List<Tuple<Range, BackendConnectionPool>> list = it.next();
				for(Iterator<Tuple<Range, BackendConnectionPool>> item = list.iterator();item.hasNext();){
					final Tuple<Range, BackendConnectionPool> tuple = item.next();
					if(tuple.right.getActive()>0){
						HeartbeatManager.addHeartbeat(new HeartbeatDelayed(5L, TimeUnit.SECONDS){
							private int count = 20;
							public Status doCheck() {
								count--;
								if(tuple.right.getActive()>0 && count>0){
									return Status.INVALID;
								}else{
									try {
										tuple.right.close();
									} catch (Exception e) {
									}
									return Status.VALID;
								}
							}

							public String getName() {
								return tuple.right.getName();
							}
							
						});
					}else{
						try {
							tuple.right.close();
						} catch (Exception e) {
						}
					}
				}
			}
		}
	}
	protected Map<String,List<Tuple<Range, BackendConnectionPool>>> load() throws Exception{
		HsbVenusConfig all = getHsbVenusConfig();
		
		Map<String,BackendConnectionPool> poolMap = initRemoteMap(all.getRemoteMap());
		
		Map<String,List<Tuple<Range, BackendConnectionPool>>> serviceMap = new HashMap<String,List<Tuple<Range, BackendConnectionPool>>>();
		
		
		//	create objectPool
		for(Map.Entry<String, RemoteServiceConfig> entry: all.getServiceMap().entrySet()){
			RemoteServiceConfig config = entry.getValue();
			BackendConnectionPool pool = null;
			if(!StringUtil.isEmpty(config.getRemote())){
				pool = poolMap.get(config.getRemote());
				if(pool == null){
					throw new ConfigurationException("service="+config.getServiceName()+",remote not found:"+config.getRemote());
				}
			}else{
				String ipAddress = config.getIpAddressList();
				if(!StringUtil.isEmpty(ipAddress)){
					pool =	createPoolFromAddressList(config.getIpAddressList());
				}else{
					throw new ConfigurationException("Service or ipAddressList or remote can not be null:"+config.getServiceName());
				}
			}
			
			try{
				Tuple<Range,BackendConnectionPool> tuple = new Tuple<Range,BackendConnectionPool>();
				tuple.left = getVersionRange(config.getVersion());
				if(tuple.left == null){
					tuple.left = new DefaultRange();
				}
				tuple.right = pool;
				
				List<Tuple<Range, BackendConnectionPool>> list = serviceMap.get(config.getServiceName());
				if(list == null){
					list = new ArrayList<Tuple<Range, BackendConnectionPool>>();
					serviceMap.put(config.getServiceName(), list);
				}
				list.add(tuple);
				
			}catch(Exception e){
				throw new ConfigurationException("init remote service config error:",e);
			}
		}
		return serviceMap;
			
	}
	
	protected abstract HsbVenusConfig getHsbVenusConfig();
	
	private static Range getVersionRange(String version){
		Range versionRange = null;
		if(!StringUtil.isEmpty(version)){
			version = version.trim();
			String[] tmps = StringUtils.split(version, "{}[], ");
			int[] rages = new int[tmps.length];
			for(int i=0;i<tmps.length; i++){
				rages[i] = Integer.valueOf(tmps[i]);
			}
			
			if(version.startsWith("[")){
				versionRange = new BetweenRange(rages);
			}else{
				versionRange = new ArrayRange(rages);
			}
			return versionRange;
		}else{
			return null;
		}
	}

}
