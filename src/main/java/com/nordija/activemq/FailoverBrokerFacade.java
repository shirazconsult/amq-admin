package com.nordija.activemq;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.activemq.web.BrokerFacade;
import org.apache.activemq.web.RemoteJMXBrokerFacade;
import org.apache.activemq.web.config.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("failoverBrokerFacade")
public class FailoverBrokerFacade {
    private final static Logger logger = LoggerFactory.getLogger(FailoverBrokerFacade.class);

    @Autowired
    private FailoverBrokerFacadeConfigurator failoverBrokerFacadeConfigurator;

    private List<RemoteJMXBrokerFacade> brokerFacadeList = new ArrayList<RemoteJMXBrokerFacade>();

    @PostConstruct
    public void init() throws Exception {
        List<AbstractConfiguration> configurations = failoverBrokerFacadeConfigurator.getConfigurations();
        for (AbstractConfiguration conf : configurations) {
            RemoteJMXBrokerFacade brokerFacade = new RemoteJMXBrokerFacade();
            brokerFacade.setConfiguration(conf);

            brokerFacadeList.add(brokerFacade);
        }
    }

    private RemoteJMXBrokerFacade last;

    public BrokerFacade getBrokerFacade() throws Exception {
        try {
            if (last != null && !last.getBrokerAdmin().isSlave()) {
                return last;
            }
        } catch (Exception e) {
            logger.info("Not master anymore. Trying the next one.");
        }
        for (RemoteJMXBrokerFacade broker : brokerFacadeList) {
            try {
                if (!broker.getBrokerAdmin().isSlave()) {
                    last = broker;
                    return broker;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        throw new IllegalStateException("No master broker found among ActiveMQ brokers");
    }

    public void setFailoverBrokerFacadeConfigurator(FailoverBrokerFacadeConfigurator failoverBrokerFacadeConfigurator) {
        this.failoverBrokerFacadeConfigurator = failoverBrokerFacadeConfigurator;
    }
}
