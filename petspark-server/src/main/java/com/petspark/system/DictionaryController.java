package com.petspark.system;

import com.petspark.common.api.ApiResponse;
import com.petspark.common.security.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dictionaries")
public class DictionaryController {

    private final SystemService service;

    public DictionaryController(SystemService service) {
        this.service = service;
    }

    @GetMapping("/types")
    @RequirePermission("dict:read")
    public ApiResponse<List<DictTypeView>> types() {
        return ApiResponse.ok(service.listDictTypes());
    }

    @PostMapping("/types")
    @RequirePermission("dict:update")
    public ApiResponse<DictTypeView> createType(@Valid @RequestBody CreateDictTypeRequest request) {
        return ApiResponse.ok(service.createDictType(request));
    }

    @GetMapping("/{typeCode}/items")
    @RequirePermission("dict:read")
    public ApiResponse<List<DictItemView>> items(@PathVariable String typeCode) {
        return ApiResponse.ok(service.listDictItems(typeCode));
    }

    @PostMapping("/{typeCode}/items")
    @RequirePermission("dict:update")
    public ApiResponse<DictItemView> createItem(
            @PathVariable String typeCode,
            @Valid @RequestBody CreateDictItemRequest request) {
        return ApiResponse.ok(service.createDictItem(typeCode, request));
    }

    @PutMapping("/items/{id}")
    @RequirePermission("dict:update")
    public ApiResponse<DictItemView> updateItem(
            @PathVariable String id,
            @Valid @RequestBody UpdateDictItemRequest request) {
        return ApiResponse.ok(service.updateDictItem(id, request));
    }
}
