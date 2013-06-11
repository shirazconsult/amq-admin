package com.nordija.activemq.monitor;

import static org.apache.camel.builder.PredicateBuilder.not;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.nordija.activemq.FailoverBrokerFacade;
import com.nordija.activemq.admin.ActiveMQBrokerAdmin;

public class BrokerMonitor extends RouteBuilder implements Monitor{
	private final static Logger logger = LoggerFactory.getLogger(BrokerMonitor.class);
	
	@Value("${broker.data.dir}")
	private String dataDir;
	@Value("${broker.data.file.prefix}")
	private String filePrefix;
	@Value("${broker.data.collection.interval}")
	private long interval;
	@Value("${broker.data.collection.count}")
	private long repeatCount;
	
	@Autowired private ActiveMQBrokerAdmin brokerAdmin;
	@Autowired private BrokerMonitorHelper brokerMonitorHelper;
	@Autowired private FailoverBrokerFacade failoverBrokerFacade;

	private AtomicBoolean isRunning = new AtomicBoolean(false);

	@Override
	public void configure() throws Exception {
		from("timer:brokerMonitorTimer?fixedRate=true&period=" + interval + "&repeatCount=" + repeatCount).routeId("broker.monitor").noAutoStartup()
		.setBody(simple(""))
		.to(ExchangePattern.InOut,
			"activemq:queue:ActiveMQ.Statistics.Broker?replyToType=Temporary&disableTimeToLive=true&requestTimeout=5000&testConnectionOnStartup=false")
		.multicast().parallelProcessing().to("direct:staticdata", "direct:dynamicdata");
		
		from("direct:dynamicdata").routeId("broker.dynamicData.processor")
		.to("bean:brokerMonitorHelper?method=extractDynamicData")
		.to("direct:dynamicDataFile");
		
		from("direct:staticdata").routeId("broker.staticData.processor")
		.choice()
			.when(not(method(BrokerMonitorHelper.class, "staticDataFileExist")))
				.to("bean:brokerMonitorHelper?method=extractStaticData")
				.to("direct:staticDataFile")
			.otherwise()
				.to("direct:trash").end();

		from("direct:dynamicDataFile")
		.to("file:"+dataDir+"?fileName="+filePrefix+"-dynamic-${date:now:yyyyMMdd}.data&fileExist=Append");

		from("direct:staticDataFile")
		.to("file:"+dataDir+"?fileName="+filePrefix+"-static-${date:now:yyyyMMdd}.data&fileExist=Append");

		from("direct:trash").stop();
	}
	
	@Override
	public void start() {
		try {
			getContext().startRoute("broker.monitor");
			isRunning.set(true);
		} catch (Exception e) {
			logger.error("Failed to start. ", e);
		}
	}

	@Override
	public void stop() {
		try {
			getContext().stopRoute("broker.monitor");
			isRunning.set(false);
		} catch (Exception e) {
			logger.error("Failed to stop. ", e);
		}
	}

	@Override
	public boolean isRunning() {
		return isRunning.get();
	}

	@Override
	public boolean testConnection() {
		try {
			failoverBrokerFacade.getBrokerFacade().getBrokerAdmin().isSlave();
		} catch (Exception ex) {
			logger.error("Could not connect to the ActiveMQ broker service.", ex);
			return false;
		}
		return true;
	}
	
	@Override
	public void resetCounters() throws Exception {
		brokerAdmin.resetStatistics();
	}
	
	@Override
	public List<String> getLastDataRow() {
		return brokerMonitorHelper.getLastDataRow();
	}

	@Override
	public List<List<String>> getDataRows(long from, long to) {
		try {
			return brokerMonitorHelper.getDataRows(from, to);
		} catch (IOException e) {
			logger.error("Could not read data.", e);
		}
		return null;
	}

	@Override
	public List<String> getDataColumns() {
		return brokerMonitorHelper.getDataColumns();
	}	
}
