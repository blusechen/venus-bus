package com.meidusa.venus.bus;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.meidusa.venus.bus.config.bean.BusConfig;
import com.meidusa.venus.bus.config.bean.RemoteServiceConfig;
import com.meidusa.venus.bus.network.BusBackendConnectionFactory;
import com.meidusa.venus.bus.xml.bean.FactoryConfig;
import com.meidusa.venus.bus.xml.bean.Remote;
import com.meidusa.venus.io.authenticate.Authenticator;
import com.meidusa.venus.io.packet.PacketConstant;
import com.meidusa.venus.util.ArrayRange;
import com.meidusa.venus.util.BetweenRange;
import com.meidusa.venus.util.DefaultRange;
import com.meidusa.venus.util.Range;
import com.meidusa.venus.util.VenusBeanUtilsBean;
import com.meidusa.toolkit.common.bean.BeanContext;
import com.meidusa.toolkit.common.bean.BeanContextBean;
import com.meidusa.toolkit.common.bean.config.ConfigUtil;
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

/**
 * 服务管理器
 * @author structchen
 *
 */
public abstract class AbstractRemoteServiceManager implements ServiceRemoteManager ,Initialisable ,BeanFactoryAware{
	
	/**
	 * 后端服务默认的连接池大小
	 */
	protected int defaultPoolSize = Remote.DEFAULT_POOL_SIZE;
	protected BeanContext beanContext;
	protected BeanFactory beanFactory;
	
	/**
	 * factory 创建connection 之后，作为connection相关的消息处理器
	 */
	protected MessageHandler messageHandler;
	
	/**
	 * 存放 后端的服务连接池
	 * key=service Name
	 * 
	 */
	protected Map<String,List<Tuple<Range, BackendConnectionPool>>> serviceMap = new HashMap<String,List<Tuple<Range, BackendConnectionPool>>>();
	
	/**
	 * Selector Connector
	 */
	protected ConnectionConnector connector;
	
	/**
	 * 实际物理服务的连接池， key是ip:port
	 */
	protected Map<String,BackendConnectionPool> realPoolMap = new HashMap<String,BackendConnectionPool>();
	
	/**
	 * 实际物理服务的连接池， key是ip:port
	 */
	protected List<BackendConnectionPool> virtualPoolMap = new ArrayList<BackendConnectionPool>();
	
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
	
	/**
	 * 初始化 service manager
	 */
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
	
	protected void removeBackendConnectionPool(String address){
		BackendConnectionPool pool = realPoolMap.remove(address);
		pool.close();
	}
	
	/**
	 * 创建多个真实地址连接的虚拟连接池
	 * @param ipList
	 * @return
	 */
	protected BackendConnectionPool createVirtualPool(String ipList[],Authenticator authenticator){
		BackendConnectionPool nioPools[] = new PollingBackendConnectionPool[ipList.length];
		
		BackendConnectionPool pool = null;
		for(int i=0;i<ipList.length;i++){
			if(realPoolMap.get(ipList[i]) != null){
				nioPools[i] = realPoolMap.get(ipList[i]);
				continue;
			}
			nioPools[i] = createRealPool(ipList[i],authenticator);
		}
		
		String poolName = "pool-"+Arrays.toString(ipList);
		pool = new MultipleLoadBalanceBackendConnectionPool(poolName,MultipleLoadBalanceObjectPool.LOADBALANCING_ROUNDROBIN,nioPools);
		pool.init();
		virtualPoolMap.add(pool);
		return pool;
	}
	
	/**
	 * 创建与真实地址连接的连接池
	 * @param address 格式 host:port
	 * @return BackendConnectionPool
	 */
	protected BackendConnectionPool createRealPool(String address, @SuppressWarnings("rawtypes") Authenticator authenticator){
		BusBackendConnectionFactory nioFactory = new BusBackendConnectionFactory();
		if(authenticator != null){
			nioFactory.setAuthenticator(authenticator);
		}
		BackendConnectionPool pool = null;
		String temp[] = StringUtil.split(address,":");
		if(temp.length>1){
			nioFactory.setHost(temp[0]);
			nioFactory.setPort(Integer.valueOf(temp[1]));
		}else{
			nioFactory.setHost(temp[0]);
			nioFactory.setPort(PacketConstant.VENUS_DEFAULT_PORT);
		}
		
		nioFactory.setConnector(this.getConnector());
		nioFactory.setMessageHandler(getMessageHandler());
		
		pool = new PollingBackendConnectionPool(address,nioFactory,defaultPoolSize);
		pool.init();
		realPoolMap.put(address, pool);
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
	
	/**
	 * 
	 * 装载配置
	 * @return
	 * Map 结构 ,Key 为 ServiceName, Value 为 List, List的Element则为 Tuple,Left为 服务的版本, Right为虚拟或者实际连接池
	 * @throws Exception
	 */
	protected abstract Map<String,List<Tuple<Range, BackendConnectionPool>>> load() throws Exception;
			
	public static Range getVersionRange(String version){
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
			return new DefaultRange();
		}
	}

}
