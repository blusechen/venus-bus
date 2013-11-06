package com.meidusa.venus.bus;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import com.meidusa.toolkit.common.bean.BeanContext;
import com.meidusa.toolkit.common.bean.BeanContextBean;
import com.meidusa.toolkit.common.bean.config.ConfigUtil;
import com.meidusa.toolkit.common.bean.util.Initialisable;
import com.meidusa.toolkit.common.bean.util.InitialisationException;
import com.meidusa.toolkit.common.heartbeat.HeartbeatDelayed;
import com.meidusa.toolkit.common.heartbeat.HeartbeatManager;
import com.meidusa.toolkit.common.heartbeat.Status;
import com.meidusa.toolkit.common.poolable.MultipleLoadBalanceObjectPool;
import com.meidusa.toolkit.common.poolable.ObjectPool;
import com.meidusa.toolkit.common.util.Tuple;
import com.meidusa.toolkit.net.BackendConnectionPool;
import com.meidusa.toolkit.net.ConnectionConnector;
import com.meidusa.toolkit.net.MessageHandler;
import com.meidusa.toolkit.net.MultipleLoadBalanceBackendConnectionPool;
import com.meidusa.toolkit.net.PollingBackendConnectionPool;
import com.meidusa.toolkit.util.StringUtil;
import com.meidusa.venus.bus.network.BusBackendConnectionFactory;
import com.meidusa.venus.bus.xml.bean.Remote;
import com.meidusa.venus.io.authenticate.Authenticator;
import com.meidusa.venus.io.packet.PacketConstant;
import com.meidusa.venus.util.Range;
import com.meidusa.venus.util.VenusBeanUtilsBean;

/**
 * ���������
 * @author structchen
 *
 */
public abstract class AbstractRemoteServiceManager implements ServiceRemoteManager ,Initialisable ,BeanFactoryAware{
	
	/**
	 * ��˷���Ĭ�ϵ����ӳش�С
	 */
	protected int defaultPoolSize = Remote.DEFAULT_POOL_SIZE;
	protected BeanContext beanContext;
	protected BeanFactory beanFactory;

	/**
	 * factory ����connection ֮����Ϊconnection��ص���Ϣ������
	 */
	protected MessageHandler messageHandler;
	
	/**
	 * ��� ��˵ķ������ӳ�
	 * key=service Name
	 * 
	 */
	protected Map<String,List<Tuple<Range, BackendConnectionPool>>> serviceMap = new HashMap<String,List<Tuple<Range, BackendConnectionPool>>>();
	
	/**
	 * Selector Connector
	 */
	protected ConnectionConnector connector;
	
	/**
	 * ʵ��������������ӳأ� key��ip:port
	 */
	protected Map<String,BackendConnectionPool> realPoolMap = new HashMap<String,BackendConnectionPool>();
	
	private Timer closeTimer = new Timer();
	/**
	 * �������ӳأ� key��ip:port
	 */
	protected Map<String,MultipleLoadBalanceBackendConnectionPool> virtualPoolMap = new HashMap<String,MultipleLoadBalanceBackendConnectionPool>();
	
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
	 * ��ʼ�� service manager
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
	 * ���������ʵ��ַ���ӵ��������ӳ�
	 * @param ipList
	 * @return
	 */
	protected synchronized BackendConnectionPool createVirtualPool(String ipList[],Authenticator authenticator){
		Arrays.sort(ipList);
		String poolName = "Virtual-"+Arrays.toString(ipList);
		MultipleLoadBalanceBackendConnectionPool multiPool = virtualPoolMap.get(poolName);
		if(multiPool != null){
			return multiPool;
		}
		
		BackendConnectionPool nioPools[] = new PollingBackendConnectionPool[ipList.length];
		
		for(int i=0;i<ipList.length;i++){
			BackendConnectionPool pool = realPoolMap.get(ipList[i]);
			if(pool != null){
				nioPools[i] = pool;
				continue;
			}else{
				pool = nioPools[i] = createRealPool(ipList[i],authenticator);
				BackendConnectionPool old = realPoolMap.put(ipList[i], pool);
				if(old != null){
					closeTimer.schedule(new ClosePoolTask(old), 30 * 1000);
				}
			}
		}
		
		multiPool = new MultipleLoadBalanceBackendConnectionPool(poolName,MultipleLoadBalanceObjectPool.LOADBALANCING_ROUNDROBIN,nioPools);
		multiPool.init();
		virtualPoolMap.put(poolName,multiPool);
		return multiPool;
	}
	
	/**
	 * ��������ʵ��ַ���ӵ����ӳ�,�����ӳش�������Ժ�,��Ҫ�������� realPoolMap 
	 * @param address ��ʽ host:port
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
	
	static class  ClosePoolTask extends TimerTask {
		BackendConnectionPool pool;
		public ClosePoolTask(BackendConnectionPool pool){
			this.pool = pool;
		}
		
		@Override
		public void run() {
			try {
				if(pool instanceof ObjectPool){
					((ObjectPool) pool).close();
				}else if(pool instanceof BackendConnectionPool){
					((BackendConnectionPool) pool).close();
				}
			} catch (Exception e) {
			}
		}
	}
	
	/**
	 * 
	 * װ������
	 * @return
	 * Map �ṹ ,Key Ϊ ServiceName, Value Ϊ List, List��Element��Ϊ Tuple,LeftΪ ����İ汾, RightΪ�������ʵ�����ӳ�
	 * @throws Exception
	 */
	protected abstract Map<String,List<Tuple<Range, BackendConnectionPool>>> load() throws Exception;
	
	protected void fixPools(){
		
		//�����������ӳ�,������������ӳ���ʵ�ʵķ�����û���õ�,��ر�
		Iterator<Map.Entry<String,MultipleLoadBalanceBackendConnectionPool>> it = this.virtualPoolMap.entrySet().iterator();
		
		while(it.hasNext()){
			BackendConnectionPool pool = it.next().getValue();
			_LABEL:{
				for(List<Tuple<Range,BackendConnectionPool>> slist: serviceMap.values()){
					for(Tuple<Range,BackendConnectionPool> tuple: slist){
						if(tuple.right == pool){
							break _LABEL;
						}
					}
				}
				pool.close();
				it.remove();
			}
		}
		
		//����ʵ�����ӳ�,��������ӳ���ʵ�ʵķ�����û���õ�,��ر�
		
		Iterator<Map.Entry<String,BackendConnectionPool>> rPools = this.realPoolMap.entrySet().iterator();
		
		while(rPools.hasNext()){
			BackendConnectionPool pool = rPools.next().getValue();
			_LABEL:{
				for(List<Tuple<Range,BackendConnectionPool>> slist: serviceMap.values()){
					for(Tuple<Range,BackendConnectionPool> tuple: slist){
						if(tuple.right instanceof MultipleLoadBalanceBackendConnectionPool){
							MultipleLoadBalanceBackendConnectionPool vPool = (MultipleLoadBalanceBackendConnectionPool)tuple.right;
							for(BackendConnectionPool item : vPool.getObjectPools()){
								if(item == pool){
									break _LABEL;
								}
							}
						}
					}
				}
				closeTimer.schedule(new ClosePoolTask(pool), 30 * 1000);
				rPools.remove();
			}
		}
		
	}
			
}