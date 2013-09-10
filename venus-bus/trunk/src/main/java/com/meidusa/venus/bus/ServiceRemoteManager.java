package com.meidusa.venus.bus;

import java.util.List;

import com.meidusa.venus.util.Range;
import com.meidusa.toolkit.common.util.Tuple;
import com.meidusa.toolkit.net.BackendConnectionPool;

public interface ServiceRemoteManager {
	public List<Tuple<Range,BackendConnectionPool>> getRemoteList(String serviceName);
}
