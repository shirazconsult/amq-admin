package com.nordija.activemq.admin;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.InvalidKeyException;
import javax.management.openmbean.TabularDataSupport;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.broker.jmx.CompositeDataConstants;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nordija.activemq.AmqSchedulerAction;

@Service
public class ActiveMQFacadeHelper {
    private final static Logger logger = LoggerFactory.getLogger(ActiveMQFacadeHelper.class);

    final static String[] headers = new String[] {
        // jms standard headers and properties
        "JMSDestination", "JMSDeliveryMode", "JMSExpiration", "JMSPriority", "JMSRedelivered", "JMSReplyTo", "JMSTimestamp",
        "JMSType", "JMSXMimieType", "JMSCorrelationID",
        // jms x-headers & x-properties
        "JMSXDeliveryCount", CompositeDataConstants.JMSXGROUP_ID, CompositeDataConstants.JMSXGROUP_SEQ, "JMSXProducerTXID",
        // ActiveMQ specific properties
        "JMSActiveMQBrokerInTime", "JMSActiveMQBrokerOutTime", CompositeDataConstants.ORIGINAL_DESTINATION};
        // Application specific properties
//        "buildNumber", "origianlDestination", "redeliveryCounter" };

    @Autowired
    private PooledConnectionFactory defaultConnectionFactory;

    /*
     * Utility methods
     */
    void removeAllScheduledMessages() throws JMSException {
        Connection connection = null;
        MessageConsumer browser = null;
        try {
            connection = defaultConnectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Start to listen to a temporary queue, which will receive scheduled messages
            Destination browseQueue = session.createTemporaryQueue();
            browser = session.createConsumer(browseQueue);
            connection.start();

            Destination schedulerTopic = session.createTopic(ScheduledMessage.AMQ_SCHEDULER_MANAGEMENT_DESTINATION);
            MessageProducer schedulerProducer = session.createProducer(schedulerTopic);
            Message request = session.createMessage();
            request.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION, ScheduledMessage.AMQ_SCHEDULER_ACTION_REMOVEALL);
            schedulerProducer.send(request);
        } catch (JMSException e) {
            logger.error("Error removing scheduled messages." + e.getMessage());
            throw e;
        } finally {
            try {
                if (browser != null) {
                    browser.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (JMSException e) {
                logger.warn("Failed to close the consumer or the connection." + e.getMessage());
            }
        }
    }

    void removeScheduledMessages(String... jobIds) throws JMSException {
        Connection connection = null;
        MessageConsumer browser = null;
        try {
            connection = defaultConnectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Start to listen to a temporary queue, which will receive scheduled messages
            Destination browseQueue = session.createTemporaryQueue();
            browser = session.createConsumer(browseQueue);
            connection.start();

            Destination schedulerTopic = session.createTopic(ScheduledMessage.AMQ_SCHEDULER_MANAGEMENT_DESTINATION);
            MessageProducer schedulerProducer = session.createProducer(schedulerTopic);
            for (String msgId : jobIds) {
                Message request = session.createMessage();
                request.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION, ScheduledMessage.AMQ_SCHEDULER_ACTION_REMOVE);
                request.setStringProperty(ScheduledMessage.AMQ_SCHEDULED_ID, msgId);
                schedulerProducer.send(request);
            }
        } catch (JMSException e) {
            logger.error("Error removing scheduled messages." + e.getMessage());
            throw e;
        } finally {
            try {
                if (browser != null) {
                    browser.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (JMSException e) {
                logger.warn("Failed to close the consumer or the connection." + e.getMessage());
            }
        }
    }

    List<Map<String, Object>> performSchedulerAction(long start, long end, AmqSchedulerAction action) throws JMSException {

        List<Map<String, Object>> mcv = new ArrayList<Map<String,Object>>();
        Connection connection = null;
        MessageConsumer browser = null;
        try {
            connection = defaultConnectionFactory.createConnection();
            ((ActiveMQConnection) connection).setWatchTopicAdvisories(false);
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Start to listen to a temporary queue, which will receive scheduled messages
            Destination browseQueue = session.createTemporaryQueue();
            browser = session.createConsumer(browseQueue);
            connection.start();

            // request for scheduled messages
            Destination schedulerTopic = session.createTopic(ScheduledMessage.AMQ_SCHEDULER_MANAGEMENT_DESTINATION);
            MessageProducer schedulerProducer = session.createProducer(schedulerTopic);
            Message request = session.createMessage();
            request.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION, ScheduledMessage.AMQ_SCHEDULER_ACTION_BROWSE);
            if (end == 0) {
                end = System.currentTimeMillis() + 365 * 24 * 3600 * 1000L; // The latest scheduled message
            }
            request.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION_START_TIME, Long.toString(start));
            request.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION_END_TIME, Long.toString(end));
            request.setJMSReplyTo(browseQueue);
            schedulerProducer.send(request);

            // recieve and process scheduled messages
            List<Message> removeRequestList = new ArrayList<Message>();
            ActiveMQMessage scheduled = null;
            while ((scheduled = (ActiveMQMessage) browser.receive(1000)) != null) {
                switch (action) {
                case view:
                    mcv.add(ActiveMQFacadeHelper.createMessageView(scheduled));
                    break;
                case remove:
                    try {
                        request = session.createMessage();
                        request.setStringProperty(ScheduledMessage.AMQ_SCHEDULER_ACTION,
                            ScheduledMessage.AMQ_SCHEDULER_ACTION_REMOVE);
                        request.setStringProperty(ScheduledMessage.AMQ_SCHEDULED_ID,
                            (String) scheduled.getProperty(ScheduledMessage.AMQ_SCHEDULED_ID));
                        removeRequestList.add(request);
                    } catch (Exception e) {
                        logger.error("Could not remove scheduled message: " + scheduled.getJMSMessageID()); // this should probably be JMSMessageID. Has to be tested.
                    }
                    break;
                default:
                    logger.error("Unknow action: " + action.name());
                }
            }
            if (action == AmqSchedulerAction.remove) {
                for (Message msg : removeRequestList) {
                    schedulerProducer.send(msg);
                }
            }
        } catch (JMSException e) {
            logger.error("Error in retrieving the scheduled messages." + e.getMessage());
            throw e;
        } finally {
            try {
                if (browser != null) {
                    browser.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (JMSException e) {
                logger.warn("Failed to close the consumer or the connection." + e.getMessage());
            }
        }
        return mcv;
    }

    static Map<String, Object> createMessageView(ActiveMQMessage msg) {
        logger.debug("Extracting message headers and properties for " + msg.getJMSMessageID());
        Map<String, Object> view = new HashMap<String, Object>();
        view.put("JMSMessageID", msg.getJMSMessageID());
        
        for (String header : headers) {
            Object res = null;
            Method getter = null;
            try {
                getter = msg.getClass().getMethod("get".concat(header), (Class<?>[]) null);
            } catch (NoSuchMethodException nsme) {
                // silently go to the next header
                continue;
            }
            Class<?> retType = getter.getReturnType();
            try {
                res = getter.invoke(msg, (Object[]) null);
                if (res == null) {
                    continue;
                }
                if (retType == long.class) {
                    res = format((Long) res);
                } else if (Destination.class.isAssignableFrom(retType)) {
                    res = getDestinationName((Destination) res);
                }
            } catch (Exception e) {
                logger.error("Unable to retrieve the header value for " + header);
            }
            if (res != null) {
                view.put(header, res);
            }
        }
        try {
            Enumeration<String> propertyNames = msg.getPropertyNames();
            while (propertyNames.hasMoreElements()) {
                String name = propertyNames.nextElement();
                Object value = msg.getProperty(name);
                view.put(name, value);
            }
        } catch (Exception e) {
            logger.error("Could not extract message properties. " + e.getMessage());
        }

        return view;
    }

    static Map<String, Object> createMessageView(CompositeData cd, boolean includeProperties, boolean includeBody) {
    	Map<String, Object> view = new HashMap<String, Object>();
    	view.put("JMSMessageID", cd.get("JMSMessageID"));
        logger.debug("Extracting message headers and properties for " + view.get("JMSMessageID"));

        // Set message type and body
        CompositeDataSupport cds = (CompositeDataSupport) cd;
        view.put("JMSType", cds.getCompositeType().getTypeName());
        if(includeBody){
        	if (view.get("JMSType").equals(ActiveMQTextMessage.class.getName())) {
        		view.put("body", (String) cds.get(CompositeDataConstants.MESSAGE_TEXT));
        	} else {
        		view.put("body", "Message content is not of type 'Text'.");
        	}
        }

        // Set headers and properties        
        for (String header : headers) {
            Object res = null;
            try {
                res = cd.get(header);
            } catch (InvalidKeyException ike) {
                res = getProperty(cd, header);
            }
            if (res == null) {
                continue;
            }
            if (res.getClass() == long.class) {
                res = format((Long) res);
            } else if (Destination.class.isAssignableFrom(res.getClass())) {
                res = getDestinationName((Destination) res);
            }
            view.put(header, res);
        }

        if(includeProperties){
        	view.putAll(getProperties(cd));
        }
        
        return view;
    }

    public static Map<String, Object> getPropertiesAndBody(CompositeData cd){
    	Map<String, Object> view = new HashMap<String, Object>();
    	if (view.get("JMSType").equals(ActiveMQTextMessage.class.getName())) {
    		view.put("body", (String) cd.get(CompositeDataConstants.MESSAGE_TEXT));
    	} else {
    		view.put("body", "Message content is not of type 'Text'.");
    	}
    	view.putAll(getProperties(cd));
    	return view;
    }
    
    public static Object getProperty(CompositeData cd, String property) {
    	TabularDataSupport[] tdsArray = getTabularDataSupport(cd);
        for (TabularDataSupport tds : tdsArray) {
            for (Entry<Object, Object> entry : tds.entrySet()) {
                CompositeDataSupport cds = (CompositeDataSupport) entry.getValue();
                if (cds.get("key").equals(property)) {
                    return cds.get("value");
                }
            }
        }
        return null;
    }

    public static Map<String, Object> getProperties(CompositeData cd){
    	Map<String, Object> result = new HashMap<String, Object>();
    	TabularDataSupport[] tdsArray = getTabularDataSupport(cd);
        for (TabularDataSupport tds : tdsArray) {
            for (Entry<Object, Object> entry : tds.entrySet()) {
                CompositeDataSupport cds = (CompositeDataSupport) entry.getValue();
                result.put("p."+(String)cds.get("key"), cds.get("value"));
            }
        }
    	return result;
    }
    
    private static TabularDataSupport[] getTabularDataSupport(CompositeData cd){
    	return new TabularDataSupport[] { (TabularDataSupport) cd.get(CompositeDataConstants.STRING_PROPERTIES),
                (TabularDataSupport) cd.get(CompositeDataConstants.BOOLEAN_PROPERTIES), (TabularDataSupport) cd.get(CompositeDataConstants.INT_PROPERTIES),
                (TabularDataSupport) cd.get(CompositeDataConstants.DOUBLE_PROPERTIES), (TabularDataSupport) cd.get(CompositeDataConstants.FLOAT_PROPERTIES),
                (TabularDataSupport) cd.get(CompositeDataConstants.SHORT_PROPERTIES), (TabularDataSupport) cd.get(CompositeDataConstants.BYTE_PROPERTIES),
                (TabularDataSupport) cd.get(CompositeDataConstants.LONG_PROPERTIES)};
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

    private static SimpleDateFormat dateFormatter = new SimpleDateFormat("yy-MM-dd HH:mm:ss:SSS z");

    static String format(long time) {
        if (time != 0) {
            return dateFormatter.format(new Date(time));
        }
        return null;
    }

}
