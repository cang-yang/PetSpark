package com.petspark.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "petspark.demo-data")
public class DemoDataProperties {

    private boolean enabled;
    private int futureDays = 7;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getFutureDays() {
        return futureDays;
    }

    public void setFutureDays(int futureDays) {
        this.futureDays = futureDays;
    }
}
