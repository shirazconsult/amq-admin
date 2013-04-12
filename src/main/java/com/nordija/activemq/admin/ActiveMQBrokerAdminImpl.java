package com.nordija.activemq.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.apache.activemq.broker.jmx.QueueViewMBean;
import org.apache.activemq.web.BrokerFacade;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nordija.activemq.FailoverBrokerFacade;
import com.nordija.activemq.NoSuchMessageException;

@Service("activeMQBrokerAdmin")
public class ActiveMQBrokerAdminImpl implements ActiveMQBrokerAdmin{
	private static final Logger logger = LoggerFactory.getLogger(ActiveMQBrokerAdminImpl.class);
	
	
    @Autowired private FailoverBrokerFacade failoverBrokerFacade;
    @Autowired private ActiveMQFacadeHelper amqFacadeHelper;
    
    @Override
    public void resetStatistics() throws Exception {
        getBrokerFacade().getBrokerAdmin().resetStatistics();
        ObjectName[] queues = getBrokerFacade().getBrokerAdmin().getQueues();
        for (ObjectName objName : queues) {
        	String queueName = objName.getKeyProperty("Destination");
        	QueueViewMBean queue = getBrokerFacade().getQueue(queueName);
        	queue.resetStatistics();
		}
    }

    private BrokerFacade getBrokerFacade() throws Exception {
        return failoverBrokerFacade.getBrokerFacade();
    }

	@Override
	public Map<String, Object> getBrokerInfo() throws Exception {
		Map<String, Object> result = new HashMap<String, Object>();
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		
		result.put("brokerName", brokerFacade.getBrokerAdmin().getBrokerName());
		result.put("brokerId", brokerFacade.getBrokerAdmin().getBrokerId());
		result.put("brokerVersion", brokerFacade.getBrokerAdmin().getBrokerVersion());
		result.put("isPersistent", brokerFacade.getBrokerAdmin().isPersistent());
		result.put("isSlave", brokerFacade.getBrokerAdmin().isSlave());
		result.put("dataDirectory", brokerFacade.getBrokerAdmin().getDataDirectory());
		
		result.put("memoryLimit", brokerFacade.getBrokerAdmin().getMemoryLimit());
		result.put("storeLimit", brokerFacade.getBrokerAdmin().getStoreLimit());
		result.put("tempLimit", brokerFacade.getBrokerAdmin().getTempLimit());

		result.put("memoryPercentUsage", brokerFacade.getBrokerAdmin().getMemoryPercentUsage());
		result.put("storePercentUsage", brokerFacade.getBrokerAdmin().getStorePercentUsage());
		result.put("tempPercentUsage", brokerFacade.getBrokerAdmin().getTempPercentUsage());
		result.put("totalConsumerCount", brokerFacade.getBrokerAdmin().getTotalConsumerCount());
		result.put("totalProducerCount", brokerFacade.getBrokerAdmin().getTotalProducerCount());
//		result.put("totalDequeueCount", brokerFacade.getBrokerAdmin().getTotalDequeueCount());
//		result.put("totalEnqueueCount", brokerFacade.getBrokerAdmin().getTotalEnqueueCount());
//		result.put("totalMessageCount", brokerFacade.getBrokerAdmin().getTotalMessageCount());
		
		return result;
	}

	private Map<String, Object> buildQueueInfo(QueueViewMBean qvb, String brokerName){
		Map<String, Object> curQ = new HashMap<String, Object>();
		
		// Elements added to this list in the same order as in ActiveMQBrokerAdmin.queueInfoColumns
		curQ.put("name", qvb.getName());
		curQ.put("queueSize", qvb.getQueueSize());
		curQ.put("enqueueCount", qvb.getEnqueueCount());
		curQ.put("dequeueCount", qvb.getDequeueCount());
		curQ.put("inFlightCount", qvb.getInFlightCount());
		curQ.put("ExpiredCount", qvb.getExpiredCount());
		curQ.put("ispatchCount", qvb.getDispatchCount());
		curQ.put("producerCount", qvb.getProducerCount());
		curQ.put("consumerCount", qvb.getConsumerCount());
		curQ.put("averageEnqueueTime", qvb.getAverageEnqueueTime());
		curQ.put("metMinEnqueueTime", qvb.getMinEnqueueTime());
		curQ.put("maxEnqueueTime", qvb.getMaxEnqueueTime());
		curQ.put("memoryUsagePortion", qvb.getMemoryUsagePortion());
		curQ.put("maxPageSize", qvb.getMaxPageSize());

		curQ.put("brokerName", brokerName);
		return curQ;
	}
	
	@Override
    public Map<String, Object> getQueueInfo(String qName) throws Exception {
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		QueueViewMBean qvb = brokerFacade.getQueue(qName);
		return buildQueueInfo(qvb, brokerFacade.getBrokerName());
	}

	
	@Override
	public int getMaxPageSize(String queue) throws Exception {
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();

		QueueViewMBean qvm = brokerFacade.getQueue(queue);
		if(qvm == null){
			return 400;  // ActiveMQ default is 400
		}
		return qvm.getMaxPageSize();
	}

	@Override
    public List<Map<String, Object>> getQueueInfo() throws Exception {
    	List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    	
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();

		Collection<QueueViewMBean> queues = brokerFacade.getQueues();
		for (QueueViewMBean qvb : queues) {
			result.add(buildQueueInfo(qvb, brokerFacade.getBrokerName()));
		}
		
		return result;
    }

	@Override
	public List<Map<String, Object>> getMessages(String queueName, String selector, int offset, int endRow) throws Exception{
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		
		QueueViewMBean queue = brokerFacade.getQueue(queueName);
		if(queue == null){
			return null;
		}
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		CompositeData[] msgs = null;
		if(selector != null){
			msgs = queue.browse(selector);
		}else{			
			msgs = queue.browse();
		}
		
		if(offset >= msgs.length){
			return null;
		}
		if(endRow == -1){
			endRow = queue.getMaxPageSize();
		}
		int upperBound = Math.min(endRow, msgs.length);
		for(int i=offset; i < upperBound; i++){
			CompositeData cd = msgs[i];
			result.add(buildMsg(ActiveMQFacadeHelper.createMessageView(cd, false, false), queueName, brokerFacade.getBrokerName()));
		}
		
		logger.debug("Requested rows {}-{}. Returning {} messages. maxBrowsePageSize is {}", new Object[]{offset, endRow, result.size(), queue.getMaxPageSize()});
				
		return result;		
	}
	
	@Override
	public List<Map<String, Object>> getMessages(String queueName, String selector) throws Exception{
		return getMessages(queueName, selector, 0, -1);
	}

	@Override
	public Map<String, Object> getMessage(String queueName, String msgId) throws Exception {
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		
		QueueViewMBean queue = brokerFacade.getQueue(queueName);
		if(queue == null){
			return null;
		}

		CompositeData[] browse = queue.browse("JMSMessageID = '"+msgId+"'");
		if(browse == null || browse.length == 0){
			return null;
		}
		
		return buildMsg(ActiveMQFacadeHelper.createMessageView(browse[0], true, true), queueName, brokerFacade.getBrokerName());
	}

	private Map<String, Object> buildMsg(Map<String, Object> msg, String qName, String broker){
		Map<String, Object> msgView = new HashMap<String, Object>();
		
		// jms headers
		msgView.put("messageID", msg.get("JMSMessageID"));
		msgView.put("timestamp", msg.get("JMSTimestamp"));
		msgView.put("type", msg.get("JMSType"));
		msgView.put("destination", msg.get("JMSDestination"));
		msgView.put("correlationID", msg.get("JMSCorrelationID"));
		msgView.put("deliveryMode", msg.get("JMSDeliveryMode"));
		msgView.put("expiration", msg.get("JMSExpiration"));
		msgView.put("priority", msg.get("JMSPriority"));
		msgView.put("redelivered", msg.get("JMSRedelivered"));
		msgView.put("replyTo", msg.get("JMSReplyTo"));			
		
		// properties 
		for (Entry<String, Object> prop : msg.entrySet()) {
			if(prop.getKey().startsWith("p.")){
				msgView.put(prop.getKey(), prop.getValue());
			}
		}
		
		// body
		if(msg.get("body") != null){
			msgView.put("body", msg.get("body"));
		}
		
		// additional data
		msgView.put("queue", qName);
		msgView.put("brokerName", broker);
		
		return msgView;
	}

	@Override
	public void deleteMessage(String queueName, List<String> msgIds) throws Exception {
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		
		QueueViewMBean queue = brokerFacade.getQueue(queueName);
		if(queue == null){
			throw new NoSuchMessageException("No such queue "+queueName, null, queueName);
		}
		
		List<String> failedMsgs = null;
		for (String id : msgIds) {
			try{
				queue.removeMessage(id);
			}catch(Exception e){
				if(failedMsgs == null){
					failedMsgs = new ArrayList<String>();
				}
				failedMsgs.add(id);
			}
		}
		if(failedMsgs != null){
			throw new RuntimeException("Failed to delete messages "+messageIdListToString(failedMsgs));
		}
	}
	
	@Override
	public void moveMessage(String fromQueue, String toQueue, List<String> msgIds) throws Exception {
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		
		QueueViewMBean queue = brokerFacade.getQueue(fromQueue);
		if(queue == null){
			throw new NoSuchMessageException("No such queue "+fromQueue, null, fromQueue);
		}

		List<String> failedMsgs = null;
		for (String id : msgIds) {
			try{
				queue.moveMessageTo(id, toQueue);
			}catch(Exception e){
				if(failedMsgs == null){
					failedMsgs = new ArrayList<String>();
				}
				failedMsgs.add(id);
			}
		}
		if(failedMsgs != null){
			throw new RuntimeException("Failed to move messages "+messageIdListToString(failedMsgs));
		}
	}

	@Override
	public void copyMessage(String fromQueue, String toQueue, List<String> msgIds) throws Exception {
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		
		QueueViewMBean queue = brokerFacade.getQueue(fromQueue);
		if(queue == null){
			throw new NoSuchMessageException("No such queue "+fromQueue, null, fromQueue);
		}

		List<String> failedMsgs = null;
		for (String id : msgIds) {
			try{
				queue.copyMessageTo(id, toQueue);
			}catch(Exception e){
				if(failedMsgs == null){
					failedMsgs = new ArrayList<String>();
				}
				failedMsgs.add(id);
			}
		}
		if(failedMsgs != null){
			throw new RuntimeException("Failed to copy messages "+messageIdListToString(failedMsgs));
		}
	}

	@Override
	public void purgeQueue(String queue) throws Exception {
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		
		QueueViewMBean q = brokerFacade.getQueue(queue);
		if(queue == null){
			throw new IllegalArgumentException("No such queue "+q);
		}
		
		q.purge();
	}

	@Override
	public void deleteQueue(String queue) throws Exception {		
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		brokerFacade.getBrokerAdmin().removeQueue(queue);
	}

	@Override
	public void createQueue(String queue) throws Exception{
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		QueueViewMBean q = brokerFacade.getQueue(queue);
		if(q != null){
			throw new IllegalArgumentException("Cannot create queue "+queue+", since it exist already.");
		}
		brokerFacade.getBrokerAdmin().addQueue(queue);		
	}
	
	static String getDestinationName(Destination dest) {
        if (dest != null) {
            try {
                if (dest instanceof Queue) {
                    return ((Queue) dest).getQueueName();
                } else if (dest instanceof Topic) {
                    return ((Topic) dest).getTopicName();
                }
            } catch (JMSException e) {
                // ignore. return null
            }
        }
        return null;
    }
	
	private String messageIdListToString(List<String> messageIdList){
		if(CollectionUtils.isEmpty(messageIdList)){
			return "";
		}
		StringBuilder sb = new StringBuilder("[");
		for (String id : messageIdList) {
			sb.append(id).append(", ");
		}
		sb.replace(sb.length()-2, sb.length()-1, "]");
		return sb.toString();
	}

	@Override
	public int moveMessage(String fromQueue, String toQueue, String selector, int maxMsgs)
			throws Exception {
		BrokerFacade brokerFacade = failoverBrokerFacade.getBrokerFacade();
		
		QueueViewMBean queue = brokerFacade.getQueue(fromQueue);
		if(queue == null){
			throw new NoSuchMessageException("No such queue "+fromQueue, null, fromQueue);
		}

		return maxMsgs == -1 ? queue.moveMatchingMessagesTo(selector, toQueue) : queue.moveMatchingMessagesTo(selector, toQueue, maxMsgs); 
	}
}
