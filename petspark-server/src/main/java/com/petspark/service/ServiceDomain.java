package com.petspark.service;

/**
 * 服务领域支撑记录。包含服务项目、资源、窗口、规格、取消轨迹的原始 DB 行，
 * 以及通知事件标识。与 {@link ServiceBookingRepository} 的 JdbcTemplate 风格一致。
 *
 * <p>关键约定：通知事件用 {@code scene + event} 组合键作为稳定标识，本模块统一用
 * {@link NotificationScene#scene} 作为 notification 表 {@code type} 列前缀；
 * 实际落到 outbox 的事件 type 由调用方用 {@code "SERVICE_" + event} 拼装。
 */
final class ServiceDomain {

    private ServiceDomain() {
    }

    /** 通知场景 + 事件组合键。scene 为 {@code SERVICE}，event 为状态机事件。 */
    enum NotificationScene {
        SERVICE("SERVICE");

        private final String scene;

        NotificationScene(String scene) {
            this.scene = scene;
        }

        String scene() {
            return scene;
        }

        /** 拼装 notification type：{@code SERVICE_<event>}，长度上限见 NotificationPayload。 */
        String type(String event) {
            return scene + "_" + event;
        }
    }
}
