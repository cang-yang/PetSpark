package com.petspark.audit;

/**
 * 审计上下文。由调用方组装后传入 {@link AuditService}。
 *
 * <p>不可变值对象；通过 {@link #withResult} / {@link #withReasonCode} 派生新实例，
 * 便于 {@code recordSuccess}/{@code recordFailure} 重载保持同一上下文其余字段。
 */
public final class AuditContext {

    private final String actorId;
    private final String actorRole;
    private final String module;
    private final String action;
    private final String objectType;
    private final String objectId;
    private final String result;
    private final String reasonCode;
    private final String ipHash;

    private AuditContext(String actorId, String actorRole, String module, String action,
                         String objectType, String objectId, String result,
                         String reasonCode, String ipHash) {
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.module = module;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.result = result;
        this.reasonCode = reasonCode;
        this.ipHash = ipHash;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getResult() { return result; }
    public String getReasonCode() { return reasonCode; }
    public String getIpHash() { return ipHash; }

    public AuditContext withResult(String result) {
        return new AuditContext(actorId, actorRole, module, action, objectType, objectId,
                result, reasonCode, ipHash);
    }

    public AuditContext withReasonCode(String reasonCode) {
        return new AuditContext(actorId, actorRole, module, action, objectType, objectId,
                result, reasonCode, ipHash);
    }

    public static final class Builder {
        private String actorId;
        private String actorRole;
        private String module;
        private String action;
        private String objectType;
        private String objectId;
        private String result;
        private String reasonCode;
        private String ipHash;

        public Builder actorId(String actorId) { this.actorId = actorId; return this; }
        public Builder actorRole(String actorRole) { this.actorRole = actorRole; return this; }
        public Builder module(String module) { this.module = module; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder objectType(String objectType) { this.objectType = objectType; return this; }
        public Builder objectId(String objectId) { this.objectId = objectId; return this; }
        public Builder result(String result) { this.result = result; return this; }
        public Builder reasonCode(String reasonCode) { this.reasonCode = reasonCode; return this; }
        public Builder ipHash(String ipHash) { this.ipHash = ipHash; return this; }

        public AuditContext build() {
            if (actorRole == null || module == null || action == null) {
                throw new IllegalStateException("actorRole, module, action are required for AuditContext");
            }
            return new AuditContext(actorId, actorRole, module, action, objectType, objectId,
                    result, reasonCode, ipHash);
        }
    }
}
