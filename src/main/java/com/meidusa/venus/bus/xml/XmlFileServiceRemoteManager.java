package com.meidusa.venus.bus.xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.RuleSet;
import org.apache.commons.digester.xmlrules.FromXmlRuleSet;

import com.meidusa.venus.bus.config.bean.HsbVenusConfig;
import com.meidusa.venus.bus.config.bean.RemoteServiceConfig;
import com.meidusa.venus.digester.DigesterRuleParser;
import com.meidusa.venus.bus.AbstractServiceRemoteManager;
import com.meidusa.toolkit.common.bean.config.ConfigUtil;
import com.meidusa.toolkit.common.bean.config.ConfigurationException;

public class XmlFileServiceRemoteManager extends AbstractServiceRemoteManager{
	
	private String[] configFiles;
	
	public String[] getConfigFiles() {
		return configFiles;
	}

	public void setConfigFiles(String[] configFiles) {
		this.configFiles = configFiles;
	}

	@Override
	protected HsbVenusConfig getHsbVenusConfig() {
		HsbVenusConfig all = new HsbVenusConfig();
		for (String configFile : configFiles) {
			configFile = (String)ConfigUtil.filter(configFile);
			RuleSet ruleSet = new FromXmlRuleSet(this.getClass().getResource("venusRemoteServiceRule.xml"),new DigesterRuleParser());
	        Digester digester = new Digester();
	        digester.setValidating(false);
	        digester.addRuleSet(ruleSet);
	        
			
			InputStream is = null;
			if(configFile.startsWith("classpath:")){
				configFile = configFile.substring("classpath:".length());
				is = this.getClass().getClassLoader().getResourceAsStream(configFile);
				if(is == null){
					throw new ConfigurationException("configFile not found in classpath="+configFile);
				}
			}else{ 
				if(configFile.startsWith("file:")){
					configFile = configFile.substring("file:".length());
				}
				try {
					is = new FileInputStream(new File(configFile));
				} catch (FileNotFoundException e) {
					throw new ConfigurationException("configFile not found with file="+configFile,e);
				}
			}
			
			try {
				HsbVenusConfig venus = (HsbVenusConfig) digester.parse(is);
				for(Map.Entry<String, RemoteServiceConfig> entry: venus.getServiceMap().entrySet()){
					RemoteServiceConfig config = entry.getValue();
					/*if(StringUtil.isEmpty(config.getRemote()) && config.getInstance() == null){
						throw new ConfigurationException("Service instance or remote property can not be null:"+configFile);
					}*/
					
					if(config.getServiceName() == null){
						throw new ConfigurationException("Service name can not be null:"+configFile);
					}
				}
				all.getRemoteMap().putAll(venus.getRemoteMap());
				all.getServiceMap().putAll(venus.getServiceMap());
			} catch (Exception e) {
				throw new ConfigurationException("can not parser xml:"+configFile,e);
			}
		}
		
		return all;
	}
}
