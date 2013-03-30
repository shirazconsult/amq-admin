package com.nordija.activemq.monitor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("brokerMonitorHelper")
public class BrokerMonitorHelper {
	private final static Logger logger = LoggerFactory.getLogger(BrokerMonitorHelper.class);
	
	private static List<String> lastDataCache = Collections.synchronizedList(new ArrayList<String>());

	@Value("${broker.data.dir}")
	private String dataDir;
	@Value("${broker.data.file.prefix}")
	private String filePrefix;
	
	// The order in this array should not be changed, since both data collection and gnuplot-scripts 
	// are based on that
	private static final String[] dynamicDataKeys = new String[]{
		"memoryUsage", "memoryPercentUsage", "storeUsage", "storePercentUsage", 
		"tempUsage", "tempPercentUsage", "consumerCount", "producerCount", "enqueueCount", 
		"dequeueCount", "dispatchCount", "inflightCount", "expiredCount", "size", 
		"averageEnqueueTime", "maxEnqueueTime", "minEnqueueTime", "messagesCached"};

	private static final String[] staticDataKeys = new String[]{
		"brokerId", "brokerName", "dataDirectory", "storeLimit", "memoryLimit", "tempLimit"};
	
	public String extractDynamicData(
			@Body Map<String, Object> data, 
			@Header(Exchange.TIMER_PERIOD) long period, 
			@Header(Exchange.TIMER_COUNTER) long counter) throws Exception {
		
		long time = period*counter;
		long now = new Date().getTime();
		StringBuilder sb = new StringBuilder();
		synchronized (lastDataCache) {
			lastDataCache.clear();
			for (String st : dynamicDataKeys) {
				lastDataCache.add(data.get(st).toString());
				sb.append(data.get(st)).append(" ");
			}
			lastDataCache.add(String.valueOf(time));
			lastDataCache.add(String.valueOf(now));
			sb.append(time).append(" ").append(now);			
		}
		
		sb.append(System.getProperty("line.separator"));
		if(dynamicDataFileExist()){
			return sb.toString();
		}
		return getDynamicDataHeader().concat(sb.toString());
	}

	public String extractStaticData(@Body Map<String, Object> data) throws Exception{
		StringBuilder sb = new StringBuilder("# ");
		for (String st : staticDataKeys) {
			sb.append(st).append(",");
		}
		sb.deleteCharAt(sb.length()-1);
		sb.append(System.getProperty("line.separator"));
		for (String st : staticDataKeys) {
			sb.append(data.get(st)).append(",");
		}
		sb.deleteCharAt(sb.length()-1);
		sb.append(System.getProperty("line.separator"));
		return sb.toString();
	}

	public boolean staticDataFileExist() {
		String today = DateTime.now().toString("yyyMMdd");
		File f = new File(dataDir, filePrefix+"-static-"+today+".data");
		return f.exists();
	}
	
	public boolean dynamicDataFileExist() {
		return getDynamicDataFile().exists();
	}	
	
	private File getDynamicDataFile(){
		String today = DateTime.now().toString("yyyMMdd");
		return new File(dataDir, filePrefix+"-dynamic-"+today+".data");		
	}
	public String getDynamicDataHeader(){
		StringBuilder sb = new StringBuilder("# ");
		int idx=1;
		for (String col : getDataColumns()) {
			sb.append(idx++).append("-").append(col).append(" ");
		}
		sb.append(System.getProperty("line.separator"));
		return sb.toString();
	}
	
	public List<String> getLastDataRow(){
		List<String> result = new ArrayList<String>();
		synchronized (lastDataCache) {
			result.addAll(lastDataCache);
		}
		return result;
	}	
	
	public List<String> getDataColumns(){
		String[] dc = Arrays.copyOf(dynamicDataKeys, dynamicDataKeys.length+2);
		dc[dc.length-2] = "time";
		dc[dc.length-1] = "utime";
		return Arrays.asList(dc);
	}

	public List<List<String>> getDataRows(long from, long to) throws IOException{
		return getDataRows(getDynamicDataFile(), from, to);
	}

	private List<List<String>> getDataRows(File file, long from, long to) throws IOException{
		List<List<String>> result = new ArrayList<List<String>>();
		if((! file.exists()) || (! file.canRead())){
			return result;
		}
		LineIterator li = FileUtils.lineIterator(file);
		String next = null;
		while(li.hasNext()){
			next = li.nextLine();
			if(next.startsWith("#")){
				continue;
			}
			String[] split = next.split(" ");
			long time = Long.parseLong(split[split.length-1]);
			if(time >= from){
				if(time > to){
					return result;
				}
				result.add(Arrays.asList(next.split(" ")));
			}
		}

		return result;
	}
}
