package com.petspark.training;

import com.petspark.service.ServiceDtos;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** 训练申请扩展字段持久化；预约主状态仍在 service_booking。 */
@Repository
class TrainingRepository {

    private final JdbcTemplate jdbcTemplate;

    TrainingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insertDetail(String id, String bookingId, TrainingDtos.TrainingApplicationRequest request) {
        jdbcTemplate.update("""
                INSERT INTO training_application_detail
                    (id, booking_id, training_goal, behavior_problem, intensity, attention_note)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, bookingId, request.trainingGoal(), blankToNull(request.behaviorProblem()),
                StringUtils.hasText(request.intensity()) ? request.intensity().trim() : "MEDIUM",
                blankToNull(request.attentionNote()));
    }

    Optional<TrainingDtos.TrainingApplicationDetailView> findDetail(String bookingId) {
        return jdbcTemplate.query("""
                SELECT training_goal, behavior_problem, intensity, attention_note
                FROM training_application_detail
                WHERE booking_id = ?
                """, rs -> rs.next() ? Optional.of(mapDetail(rs)) : Optional.empty(), bookingId);
    }

    boolean resourceBelongsToTraining(String resourceId) {
        Boolean result = jdbcTemplate.query("""
                SELECT si.kind = 'TRAINING'
                FROM service_resource sr
                JOIN service_item si ON si.id = sr.service_item_id
                WHERE sr.id = ? AND sr.deleted_at IS NULL AND si.deleted_at IS NULL
                """, rs -> rs.next() ? rs.getBoolean(1) : null, resourceId);
        return Boolean.TRUE.equals(result);
    }

    ServiceDtos.ServiceItemUpsertRequest forceTraining(ServiceDtos.ServiceItemUpsertRequest request) {
        return new ServiceDtos.ServiceItemUpsertRequest("TRAINING", request.code(), request.name(),
                request.description(), request.qualification(), request.availabilityNote(), request.exceptionRule(),
                request.basePrice(), request.status());
    }

    ServiceDtos.ServiceItemQuery itemQuery(TrainingDtos.TrainingItemQuery query) {
        ServiceDtos.ServiceItemQuery q = new ServiceDtos.ServiceItemQuery();
        q.setKind("TRAINING");
        q.setStatus(query.getStatus());
        q.setKeyword(query.getKeyword());
        q.setPage(query.getPage());
        q.setSize(query.getSize());
        return q;
    }

    ServiceDtos.MyBookingQuery myBookingQuery(TrainingDtos.TrainingBookingQuery query) {
        ServiceDtos.MyBookingQuery q = new ServiceDtos.MyBookingQuery();
        q.setKind("TRAINING");
        q.setStatus(query.getStatus());
        q.setPage(query.getPage());
        q.setSize(query.getSize());
        return q;
    }

    ServiceDtos.AdminBookingQuery adminBookingQuery(TrainingDtos.TrainingBookingQuery query) {
        ServiceDtos.AdminBookingQuery q = new ServiceDtos.AdminBookingQuery();
        q.setKind("TRAINING");
        q.setStatus(query.getStatus());
        q.setKeyword(query.getKeyword());
        q.setPage(query.getPage());
        q.setSize(query.getSize());
        return q;
    }

    private TrainingDtos.TrainingApplicationDetailView mapDetail(ResultSet rs) throws SQLException {
        return new TrainingDtos.TrainingApplicationDetailView(
                rs.getString("training_goal"),
                rs.getString("behavior_problem"),
                rs.getString("intensity"),
                rs.getString("attention_note"));
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
