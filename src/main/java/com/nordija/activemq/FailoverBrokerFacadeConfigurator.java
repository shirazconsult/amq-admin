package com.nordija.activemq;

import java.util.List;

import org.apache.activemq.web.config.AbstractConfiguration;

public interface FailoverBrokerFacadeConfigurator {
    List<AbstractConfiguration> getConfigurations();
}
