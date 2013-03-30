package com.nordija.activemq.monitor;

import java.util.List;

import org.springframework.context.Lifecycle;

public interface Monitor extends Lifecycle {
	public boolean testConnection();
	public void resetCounters() throws Exception;
	public List<String> getLastDataRow();
	public List<List<String>> getDataRows(long from, long to);
	public List<String> getDataColumns();
}
