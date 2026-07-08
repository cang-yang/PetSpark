package com.petspark.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

/**
 * 审计日志领域模型，对应 {@code audit_log} 表。只追加，不更新、不删除
 * （见 05-数据库设计说明书 §2：审计记录只允许状态失效或隐私清除，不走普通物理删除）。
 *
 * <p>{@code ipHash} 只存客户端 IP 的哈希，不存明文 IP；{@code reasonCode} 仅在
 * {@code result=FAILURE} 时填写。字段语义对应接口设计 §6 审计契约。
 */
@JsonPropertyOrder({"id", "requestId", "actorId", "actorRole", "module", "action",
        "objectType", "objectId", "result", "reasonCode", "ipHash", "createdAt"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AuditLog {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";

    private final String id;
    private final String requestId;
    private final String actorId;
    private final String actorRole;
    private final String module;
    private final String action;
    private final String objectType;
    private final String objectId;
    private final String result;
    private final String reasonCode;
    private final String ipHash;
    private final Instant createdAt;

    public AuditLog(String id, String requestId, String actorId, String actorRole,
                    String module, String action, String objectType, String objectId,
                    String result, String reasonCode, String ipHash, Instant createdAt) {
        this.id = id;
        this.requestId = requestId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.module = module;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.result = result;
        this.reasonCode = reasonCode;
        this.ipHash = ipHash;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getResult() { return result; }
    public String getReasonCode() { return reasonCode; }
    public String getIpHash() { return ipHash; }
    public Instant getCreatedAt() { return createdAt; }
}
