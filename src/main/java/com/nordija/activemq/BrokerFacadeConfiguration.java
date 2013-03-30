package com.nordija.activemq;

import java.util.Collection;

import javax.jms.ConnectionFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.activemq.web.config.AbstractConfiguration;

public class BrokerFacadeConfiguration extends AbstractConfiguration {

    private String jmsUser;
    private String jmsPassword;
    private String jmxUser;
    private String jmxPassword;

    private String jmsUrl;
    private String jmxUrl;

    public BrokerFacadeConfiguration() {
        super();
    }

    public BrokerFacadeConfiguration(String jmsUser, String jmsPassword, String jmxUser, String jmxPassword, String jmsUrl, String jmxUrl) {
        super();
        this.jmsUser = jmsUser;
        this.jmsPassword = jmsPassword;
        this.jmxUser = jmxUser;
        this.jmxPassword = jmxPassword;
        this.jmsUrl = jmsUrl;
        this.jmxUrl = jmxUrl;
    }

    @Override
    public ConnectionFactory getConnectionFactory() {
        return makeConnectionFactory(jmsUrl, jmsUser, jmsPassword);
    }

    @Override
    public Collection<JMXServiceURL> getJmxUrls() {
        return makeJmxUrls(jmxUrl);
    }

    @Override
    public String getJmxPassword() {
        return jmxPassword;
    }

    @Override
    public String getJmxUser() {
        return jmxUser;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (jmsUrl == null ? 0 : jmsUrl.hashCode());
        result = prime * result + (jmxUrl == null ? 0 : jmxUrl.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BrokerFacadeConfiguration other = (BrokerFacadeConfiguration) obj;
        if (jmsUrl == null) {
            if (other.jmsUrl != null) {
                return false;
            }
        } else if (!jmsUrl.equals(other.jmsUrl)) {
            return false;
        }
        if (jmxUrl == null) {
            if (other.jmxUrl != null) {
                return false;
            }
        } else if (!jmxUrl.equals(other.jmxUrl)) {
            return false;
        }
        return true;
    }

}
