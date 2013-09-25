package com.meidusa.venus.bus.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.meidusa.toolkit.common.util.Tuple;
import com.meidusa.toolkit.net.BackendConnectionPool;
import com.meidusa.venus.bus.AbstractRemoteServiceManager;
import com.meidusa.venus.client.simple.SimpleServiceFactory;
import com.meidusa.venus.exception.VenusExceptionFactory;
import com.meidusa.venus.io.authenticate.Authenticator;
import com.meidusa.venus.service.registry.ServiceDefinition;
import com.meidusa.venus.service.registry.ServiceRegistry;
import com.meidusa.venus.util.Range;
import com.meidusa.venus.util.RangeUtil;

/**
 * 通过注册中心进行登记的远程服务管理
 * @author Structchen
 *
 */
public class RegistryRemoteServiceManager extends AbstractRemoteServiceManager {
	
	/**
	 * 注册中心主机IP
	 */
	private String host;
	
	/**
	 * 注册中心服务端口
	 */
	private int port;
	
	/**
	 * 与注册中心采用的认证方式
	 */
	private Authenticator authenticator;
	
	private VenusExceptionFactory  venusExceptionFactory;
	
	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
	public Authenticator getAuthenticator() {
		return authenticator;
	}

	public void setAuthenticator(Authenticator authenticator) {
		this.authenticator = authenticator;
	}

	public VenusExceptionFactory getVenusExceptionFactory() {
		return venusExceptionFactory;
	}

	public void setVenusExceptionFactory(VenusExceptionFactory venusExceptionFactory) {
		this.venusExceptionFactory = venusExceptionFactory;
	}

	@Override
	protected Map<String, List<Tuple<Range, BackendConnectionPool>>> load()
			throws Exception {
		
		SimpleServiceFactory factory = new SimpleServiceFactory(host,port);
		if(authenticator != null){
			factory.setAuthenticator(authenticator);
		}
		factory.setVenusExceptionFactory(venusExceptionFactory);
		ServiceRegistry registry = factory.getService(ServiceRegistry.class);
		List<ServiceDefinition> list = registry.getServiceDefinitions();
		
		Map<String,List<Tuple<Range, BackendConnectionPool>>> serviceMap = new HashMap<String,List<Tuple<Range, BackendConnectionPool>>>();
		
		for(ServiceDefinition definition : list){
			List<Tuple<Range, BackendConnectionPool>> l = serviceMap.get(definition.getName());
			if(l == null){
				l = new ArrayList<Tuple<Range, BackendConnectionPool>>();
				serviceMap.put(definition.getName(), l);
			}

			String[] ips = definition.getIpAddress().toArray(new String[]{});
			BackendConnectionPool pool = this.createVirtualPool(ips, authenticator);
			Range range = RangeUtil.getVersionRange(definition.getVersionRange());
			Tuple<Range, BackendConnectionPool> tuple = new Tuple<Range, BackendConnectionPool>(range,pool);
			l.add(tuple);
		}
		factory.destroy();
		return serviceMap;
	}

}
