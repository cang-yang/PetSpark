package com.petspark.training;

import com.petspark.common.api.PageResult;
import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.service.ServiceBookingService;
import com.petspark.service.ServiceDtos;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 训练服务应用层薄封装：强制 kind=TRAINING，复用服务预约状态机与容量控制。 */
@Service
public class TrainingService {

    private static final String TRAINING = "TRAINING";

    private final ServiceBookingService serviceBookingService;
    private final TrainingRepository repository;

    public TrainingService(ServiceBookingService serviceBookingService, TrainingRepository repository) {
        this.serviceBookingService = serviceBookingService;
        this.repository = repository;
    }

    public PageResult<TrainingDtos.TrainingItemView> listItems(TrainingDtos.TrainingItemQuery query) {
        PageResult<ServiceDtos.ServiceItemView> page = serviceBookingService.listItems(repository.itemQuery(query));
        return new PageResult<>(page.getItems().stream().map(TrainingDtos.TrainingItemView::from).toList(),
                page.getPage(), page.getSize(), page.getTotal());
    }

    public TrainingDtos.TrainingItemView getItem(String id) {
        return TrainingDtos.TrainingItemView.from(requireTrainingItem(id));
    }

    public PageResult<ServiceDtos.ServiceResourceView> listResources(ServiceDtos.ServiceResourceQuery query) {
        if (query.getServiceItemId() == null || query.getServiceItemId().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FIELD_001);
        }
        requireTrainingItem(query.getServiceItemId());
        return serviceBookingService.listResources(query);
    }

    public PageResult<ServiceDtos.ServiceSlotView> listSlots(ServiceDtos.ServiceSlotQuery query) {
        if (query.getResourceId() == null || query.getResourceId().isBlank()
                || !repository.resourceBelongsToTraining(query.getResourceId())) {
            throw new BusinessException(ErrorCode.SERVICE_RESOURCE_NOT_FOUND_001);
        }
        return serviceBookingService.listSlots(query);
    }

    @Transactional
    public TrainingDtos.TrainingBookingView create(TrainingDtos.TrainingApplicationRequest request, String userId) {
        requireTrainingItem(request.serviceItemId());
        ServiceDtos.ServiceBookingView booking = serviceBookingService.create(request.toServiceRequest(), userId);
        repository.insertDetail(UUID.randomUUID().toString(), booking.id(), request);
        return withDetail(booking);
    }

    public PageResult<TrainingDtos.TrainingBookingView> listMy(String userId, TrainingDtos.TrainingBookingQuery query) {
        PageResult<ServiceDtos.ServiceBookingView> page = serviceBookingService.listMy(userId, repository.myBookingQuery(query));
        return new PageResult<>(page.getItems().stream().map(this::withDetail).toList(),
                page.getPage(), page.getSize(), page.getTotal());
    }

    public TrainingDtos.TrainingBookingView getForUser(String id, String userId, boolean isAdmin) {
        ServiceDtos.ServiceBookingView booking = serviceBookingService.getForUser(id, userId, isAdmin);
        ensureTrainingBooking(booking);
        return withDetail(booking);
    }

    @Transactional
    public TrainingDtos.TrainingBookingView cancel(String id, ServiceDtos.ServiceBookingCancelRequest request,
            String userId, boolean isAdmin) {
        ensureTrainingBooking(serviceBookingService.getForUser(id, userId, isAdmin));
        return withDetail(serviceBookingService.cancel(id, request, userId, isAdmin));
    }

    @Transactional
    public TrainingDtos.TrainingBookingView exception(String id, ServiceDtos.ServiceBookingExceptionRequest request,
            String userId, boolean isAdmin) {
        ensureTrainingBooking(serviceBookingService.getForUser(id, userId, isAdmin));
        return withDetail(serviceBookingService.exception(id, request, userId, isAdmin));
    }

    public PageResult<TrainingDtos.TrainingBookingView> listAdmin(TrainingDtos.TrainingBookingQuery query) {
        PageResult<ServiceDtos.ServiceBookingView> page = serviceBookingService.listAdmin(repository.adminBookingQuery(query));
        return new PageResult<>(page.getItems().stream().map(this::withDetail).toList(),
                page.getPage(), page.getSize(), page.getTotal());
    }

    @Transactional
    public TrainingDtos.TrainingBookingView transition(String id, ServiceDtos.ServiceBookingTransitionRequest request,
            String operatorId) {
        ensureTrainingBooking(serviceBookingService.getForUser(id, operatorId, true));
        return withDetail(serviceBookingService.transition(id, request, operatorId));
    }

    @Transactional
    public TrainingDtos.TrainingItemView upsertItem(String id, ServiceDtos.ServiceItemUpsertRequest request,
            boolean insert) {
        ServiceDtos.ServiceItemView item = serviceBookingService.upsertItem(id, repository.forceTraining(request), insert);
        return TrainingDtos.TrainingItemView.from(item);
    }

    @Transactional
    public int deleteItem(String id) {
        requireTrainingItem(id);
        return serviceBookingService.deleteItem(id);
    }

    @Transactional
    public ServiceDtos.ServiceResourceView upsertResource(String id, ServiceDtos.ServiceResourceUpsertRequest request,
            boolean insert) {
        requireTrainingItem(request.serviceItemId());
        return serviceBookingService.upsertResource(id, request, insert);
    }

    @Transactional
    public java.util.List<ServiceDtos.ServiceSlotView> createSlots(ServiceDtos.ServiceSlotCreateRequest request) {
        if (request.resourceId() == null || request.resourceId().isBlank()
                || !repository.resourceBelongsToTraining(request.resourceId())) {
            throw new BusinessException(ErrorCode.SERVICE_RESOURCE_NOT_FOUND_001);
        }
        return serviceBookingService.createSlots(request);
    }

    private ServiceDtos.ServiceItemView requireTrainingItem(String id) {
        ServiceDtos.ServiceItemView item = serviceBookingService.getItem(id);
        if (!TRAINING.equals(item.kind())) {
            throw new BusinessException(ErrorCode.SERVICE_ITEM_NOT_FOUND_001);
        }
        return item;
    }

    private void ensureTrainingBooking(ServiceDtos.ServiceBookingView booking) {
        if (booking == null || !TRAINING.equals(booking.kind())) {
            throw new BusinessException(ErrorCode.SERVICE_BOOKING_NOT_FOUND_001);
        }
    }

    private TrainingDtos.TrainingBookingView withDetail(ServiceDtos.ServiceBookingView booking) {
        ensureTrainingBooking(booking);
        return TrainingDtos.TrainingBookingView.from(booking,
                repository.findDetail(booking.id()).orElse(null));
    }
}
