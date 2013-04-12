package com.nordija.activemq.admin;

import java.util.List;
import java.util.Map;

public interface ActiveMQBrokerAdmin {
	// Don't change the order or the name of the entries in this list!!
	public static final String[] queueInfoColNames = new String[]{
		"Name", "Queue Size", "Enqueue Count", "Dequeue Count", "InFlight Count",
		"Expired Count", "Dispatch Count", "Producer Count", "Consumer Count", 
		"Average Enqueue Time", "Min Enqueue Time",  "Max Enqueue Time", 
		 "Memory Usage Portion", "Max Page Size"};
	
	public static final String[] queueInfoColTypes = new String[]{
		String.class.getName(), Long.TYPE.getName(), Long.TYPE.getName(), Long.TYPE.getName(),
		Long.TYPE.getName(), Long.TYPE.getName(), Long.TYPE.getName(), Long.TYPE.getName(),
		Long.TYPE.getName(), Double.TYPE.getName(), Long.TYPE.getName(), Long.TYPE.getName(),
		Float.TYPE.getName(), Integer.TYPE.getName()
	};
	
	public static final String[] msgInfoColNames = new String[]{
		"JMSMessageID", "JMSTimestamp", "JMSType", "JMSDestination", "JMSCorrelationID", 
		"JMSDeliveryMode", "JMSExpiration", "JMSPriority", "JMSRedelivered", "JMSReplyTo",
		"JMSXMimeType"
	};

	public static final String[] msgInfoColTypes = new String[]{
		String.class.getName(), Long.TYPE.getName(), String.class.getName(), 
		String.class.getName(), String.class.getName(), Integer.TYPE.getName(),
		Long.TYPE.getName(), Integer.TYPE.getName(), Boolean.TYPE.getName(),
		String.class.getName(), String.class.getName()
	};

	public void resetStatistics() throws Exception;
	public Map<String, Object> getBrokerInfo() throws Exception;
	List<Map<String, Object>> getQueueInfo() throws Exception;
	Map<String, Object> getQueueInfo(String qName) throws Exception;
	Map<String, Object> getMessage(String queueName, String msgId) throws Exception;
	void deleteMessage(String queueName, List<String> msgIds) throws Exception;
	void moveMessage(String fromQueue, String toQueue, List<String> msgIds) throws Exception;
	int moveMessage(String fromQueue, String toQueue, String selector, int maxMsgs) throws Exception;
	void copyMessage(String fromQueue, String toQueue, List<String> msgIds) throws Exception;
	void purgeQueue(String queue) throws Exception;
	void deleteQueue(String queue) throws Exception;
	void createQueue(String queue) throws Exception;
	List<Map<String, Object>> getMessages(String queueName, String selector) throws Exception;
	List<Map<String, Object>> getMessages(String queueName, String selector,
			int offset, int numOfRows) throws Exception;
	int getMaxPageSize(String queue) throws Exception;
}
