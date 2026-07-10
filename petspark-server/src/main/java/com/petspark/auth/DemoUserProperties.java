package com.petspark.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "petspark.demo-users")
public class DemoUserProperties {

    private boolean enabled;
    private String adminUsername = "admin";
    private String adminEmail = "admin@petspark.local";
    private String adminNickname = "平台管理员";
    private String adminPassword = "";
    private String memberUsername = "demo";
    private String memberEmail = "demo@petspark.local";
    private String memberNickname = "演示用户";
    private String memberPassword = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getAdminNickname() { return adminNickname; }
    public void setAdminNickname(String adminNickname) { this.adminNickname = adminNickname; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public String getMemberUsername() { return memberUsername; }
    public void setMemberUsername(String memberUsername) { this.memberUsername = memberUsername; }
    public String getMemberEmail() { return memberEmail; }
    public void setMemberEmail(String memberEmail) { this.memberEmail = memberEmail; }
    public String getMemberNickname() { return memberNickname; }
    public void setMemberNickname(String memberNickname) { this.memberNickname = memberNickname; }
    public String getMemberPassword() { return memberPassword; }
    public void setMemberPassword(String memberPassword) { this.memberPassword = memberPassword; }
}
