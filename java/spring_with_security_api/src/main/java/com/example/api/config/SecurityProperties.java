package com.example.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Set;


@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private Set<String> trustedDomains;

    public Set<String> getTrustedDomains() {
        return trustedDomains;
    }

    public void setTrustedDomains(Set<String> trustedDomains) {
        this.trustedDomains = trustedDomains;
    }
}
