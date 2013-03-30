package com.nordija.activemq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.List;

import junit.framework.Assert;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit4.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import com.nordija.activemq.monitor.BrokerMonitor;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(locations = { "classpath:applicationContext-test.xml" })
@UseAdviceWith(true)
public class BrokerMonitorTest {
	@Value("${broker.data.dir}")
	private String dataDir;
	@Value("${broker.data.file.prefix}")
	private String filePrefix;
	
	@Autowired
	private ModelCamelContext context;
	@Autowired
	private BrokerMonitor brokerMonitor;
	
	@Produce(uri = "activemq:queue:stat.inbound")
	private ProducerTemplate template;

	@EndpointInject(uri = "mock:direct:dynamicDataFile")
	private MockEndpoint dynamicDataMock;
	@EndpointInject(uri = "mock:direct:staticDataFile")
	private MockEndpoint staticDataMock;

	@Test
	public void testDynamicData() throws Exception{
		RouteDefinition dynDataProcessor = context.getRouteDefinition("broker.dynamicData.processor");
		dynDataProcessor.adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				mockEndpoints(".*dynamicDataFile.*");
			}
		});
		RouteDefinition statDataProcessor = context.getRouteDefinition("broker.staticData.processor");
		statDataProcessor.adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				mockEndpoints(".*staticDataFile.*");
			}
		});

		// expectations
		dynamicDataMock.expectedMessageCount(3);
		staticDataMock.expectedMessageCount(1);
		
		context.start();
		brokerMonitor.start();
		
		for (int i=0; i<=100; i++) {			
			template.sendBody("Test message");
			Thread.sleep(50);
		}
		
		// assertions
		dynamicDataMock.assertIsSatisfied(3000);
		staticDataMock.assertIsSatisfied(3000);
		
		List<Exchange> exchanges = dynamicDataMock.getExchanges();
		for (Exchange exc : exchanges) {
			String body = exc.getIn().getBody(String.class);
			Assert.assertFalse(StringUtils.isBlank(body));
		}
		
		exchanges = staticDataMock.getExchanges();
		for (Exchange exc : exchanges) {
			String body = exc.getIn().getBody(String.class);
			Assert.assertTrue(body.contains("brokerName"));
			Assert.assertTrue(body.contains("brokerId"));
		}
		
		File staticdataDir = new File(dataDir);
		String[] brokerDataFiles = getBrokerDataFiles();
		for (String f : brokerDataFiles) {
			File file = new File(staticdataDir, f);
			Assert.assertTrue(file.exists());
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line = br.readLine();
			Assert.assertFalse(StringUtils.isBlank(line));
		}
	}
	
	@Before
	public void setUp(){
		String[] listOfFiles = getBrokerDataFiles();
		for (String f : listOfFiles) {
			new File(dataDir, f).delete();
		}
	}
	
	private String[] getBrokerDataFiles(){
		File staticdataDir = new File(dataDir);
		if(staticdataDir.exists()){
			return staticdataDir.list(new FilenameFilter() {				
				@Override
				public boolean accept(File dir, String filename) {
					return (filename.startsWith(filePrefix));
				}
			});
		}else{
			throw new IllegalArgumentException(dataDir+" does not exist.");
		}
	}
}
