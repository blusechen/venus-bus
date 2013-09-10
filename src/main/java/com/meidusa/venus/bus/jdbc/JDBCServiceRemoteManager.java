package com.meidusa.venus.bus.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.log4j.Logger;

import com.meidusa.venus.bus.config.bean.HsbVenusConfig;
import com.meidusa.venus.bus.config.bean.RemoteServiceConfig;
import com.meidusa.venus.client.xml.bean.FactoryConfig;
import com.meidusa.venus.client.xml.bean.PoolConfig;
import com.meidusa.venus.client.xml.bean.Remote;
import com.meidusa.venus.bus.AbstractServiceRemoteManager;

public class JDBCServiceRemoteManager extends AbstractServiceRemoteManager {
	private static Logger logger = Logger.getLogger(JDBCServiceRemoteManager.class);

	private MysqlConnection connection = new MysqlConnection();
	private String jdbcUrl;

	public String getJdbcUrl() {
		return jdbcUrl;
	}

	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}

	@Override
	protected HsbVenusConfig getHsbVenusConfig() {
		
		connection.connect(jdbcUrl);
		HsbVenusConfig venusConfig = new HsbVenusConfig();
		fillRemotes(venusConfig, connection);
		fillServices(venusConfig, connection);
		connection.close();
		return venusConfig;
	}

	private void fillRemotes(HsbVenusConfig venusConfig, MysqlConnection conn) {
		ResultSet result = conn.executeQuery("SELECT * FROM REMOTE");
		try {
			while (result.next()) {
				Remote remote = new Remote();
				remote.setName(result.getString("NAME"));
				FactoryConfig factoryConfig = new FactoryConfig();
				String strValue;
				int intValue;
				strValue = result.getString("IP_ADDRESS_LIST");
				if(strValue != null && !strValue.equals("")) {
					factoryConfig.setIpAddressList(strValue);
				}
				intValue = result.getInt("SEND_BUFFER_SIZE");
				if(intValue != 0 ) {
					factoryConfig.setSendBufferSize(intValue);
				}
				intValue = result.getInt("RECEIVE_BUFFER_SIZE");
				if(intValue != 0 ) {
					factoryConfig.setReceiveBufferSize(intValue);
				}
				intValue = result.getInt("SOCKET_TIMEOUT");
				if(intValue != 0 ) {
					factoryConfig.setSoTimeout(intValue);
				}
				intValue = result.getInt("CONNECTION_TIMEOUT");
				if(intValue != 0 ) {
					factoryConfig.setCoTimeout(intValue);
				}
				intValue = result.getInt("SERIALIZE_TYPE");
				if(intValue != 0 ) {
					factoryConfig.setSendBufferSize(intValue);
				}
				PoolConfig poolConfig = new PoolConfig();
				intValue = result.getInt("MAX_ACTIVE");
				if(intValue != 0 ) {
					poolConfig.setMaxActive(intValue);
				}
				intValue = result.getInt("MAX_IDLE");
				if(intValue != 0 ) {
					poolConfig.setMaxIdle(intValue);
				}
				intValue = result.getInt("MAX_IDLE");
				if(intValue != 0 ) {
					poolConfig.setMaxIdle(intValue);
				}
				intValue = result.getInt("MIN_IDLE");
				if(intValue != 0 ) {
					poolConfig.setMinIdle(intValue);
				}
				intValue = result.getInt("MIN_EVICTABLE_IDLE_TIME_MILLIS");
				if(intValue != 0 ) {
					poolConfig.setMinEvictableIdleTimeMillis(intValue);
				}
				intValue = result.getInt("TIME_BETWEEN_EVICTION_RUNS_MILLIS");
				if(intValue != 0 ) {
					poolConfig.setTimeBetweenEvictionRunsMillis(intValue);
				}
				poolConfig.setTestOnBorrow(result.getBoolean("TEST_ON_BORROW"));
				poolConfig.setTestWhileIdle(result.getBoolean("TEST_WHILE_IDLE"));
				remote.setFactory(factoryConfig);
				remote.setPool(poolConfig);
				venusConfig.addRemote(remote);
			}
		} catch (SQLException e) {
			logger.fatal("read database failure", e);
		}

	}

	private void fillServices(HsbVenusConfig venusConfig, MysqlConnection conn) {
		ResultSet result = conn.executeQuery("SELECT * FROM SERVICE");
		try {
			while (result.next()) {
				RemoteServiceConfig config = new RemoteServiceConfig();
				config.setRemote(result.getString("REMOTE"));
				config.setIpAddressList(result.getString("IP_ADDRESS_LIST"));
				config.setServiceName(result.getString("SERVICE_NAME"));
				config.setSoTimeout(result.getInt("SOCKET_TIMEOUT"));
				config.setVersion(result.getString("VERSION"));
				venusConfig.addService(config);

			}
		} catch (SQLException e) {
			logger.fatal("read database failure", e);
		}

	}
	
	public static void main(String[] args) {
		JDBCServiceRemoteManager remoteManager = new JDBCServiceRemoteManager();
		remoteManager.setJdbcUrl("jdbc:mysql://localhost:3306/hsb?user=root&password=059545111");
		HsbVenusConfig config =  remoteManager.getHsbVenusConfig();
		System.out.println(config);
	}
}
