package com.nordija.activemq.admin;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.activemq.web.config.AbstractConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nordija.activemq.BrokerFacadeConfiguration;
import com.nordija.activemq.FailoverBrokerFacadeConfigurator;

@Service("failoverBrokerFacadeConfigurator")
public class FailoverBrokerFacadeConfiguratorImpl implements FailoverBrokerFacadeConfigurator {
    @Value("${activemq.broker.url}")
    private String jmsUrl;
    @Value("${activemq.broker.jms.user}")
    private String jmsUser;
    @Value("${activemq.broker.jms.password}")
    private String jmsPassword;

    @Value("${activemq.broker.jmx.url}")
    private String jmxUrl;
    @Value("${activemq.broker.jmx.user}")
    private String jmxUser;
    @Value("${activemq.broker.jmx.password}")
    private String jmxPassword;

    private List<AbstractConfiguration> configurations = new ArrayList<AbstractConfiguration>();

    @PostConstruct
    public void init() {
        final String[] jmxUrls = jmxUrl.split(",");

        for (int i = 0; i < jmxUrls.length; i++) {
            configurations.add(new BrokerFacadeConfiguration(jmsUser, jmsPassword, jmxUser, jmxPassword, jmsUrl, jmxUrls[i]));
        }
    }

    public List<AbstractConfiguration> getConfigurations() {
        return configurations;
    }

}
