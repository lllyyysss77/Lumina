package com.lumina.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ApiResponse;
import com.lumina.entity.LlmModel;
import com.lumina.service.LlmModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/llm-models")
public class LlmModelController {

    @Autowired
    private LlmModelService llmModelService;

    @GetMapping
    public ApiResponse<List<LlmModel>> getAllLlmModels() {
        List<LlmModel> models = llmModelService.list();
        return ApiResponse.success(models);
    }

    @GetMapping("/{modelName}")
    public ApiResponse<LlmModel> getLlmModelByName(@PathVariable String modelName) {
        LlmModel model = llmModelService.getById(modelName);
        if (model == null) {
            throw new IllegalArgumentException("LlmModel not found with name: " + modelName);
        }
        return ApiResponse.success(model);
    }

    @PostMapping
    public ApiResponse<LlmModel> createLlmModel(@RequestBody LlmModel model) {
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        boolean success = llmModelService.save(model);
        if (!success) {
            throw new IllegalArgumentException("Failed to create llm model");
        }
        return ApiResponse.success(model);
    }

    @PutMapping("/{modelName}")
    public ApiResponse<LlmModel> updateLlmModel(@PathVariable String modelName, @RequestBody LlmModel model) {
        model.setModelName(modelName);
        model.setUpdatedAt(LocalDateTime.now());
        boolean success = llmModelService.updateById(model);
        if (!success) {
            throw new IllegalArgumentException("LlmModel not found with name: " + modelName);
        }
        return ApiResponse.success(model);
    }

    @DeleteMapping("/{modelName}")
    public ApiResponse<Void> deleteLlmModel(@PathVariable String modelName) {
        boolean success = llmModelService.removeById(modelName);
        if (!success) {
            throw new IllegalArgumentException("LlmModel not found with name: " + modelName);
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/page")
    public ApiResponse<Page<LlmModel>> getLlmModelsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<LlmModel> page = llmModelService.page(new Page<>(current, size));
        return ApiResponse.success(page);
    }
}
