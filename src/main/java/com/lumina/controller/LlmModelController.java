package com.lumina.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.entity.LlmModel;
import com.lumina.service.LlmModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/llm-models")
public class LlmModelController {

    @Autowired
    private LlmModelService llmModelService;

    @GetMapping
    public ResponseEntity<List<LlmModel>> getAllLlmModels() {
        List<LlmModel> models = llmModelService.list();
        return ResponseEntity.ok(models);
    }

    @GetMapping("/{modelName}")
    public ResponseEntity<LlmModel> getLlmModelByName(@PathVariable String modelName) {
        LlmModel model = llmModelService.getById(modelName);
        if (model == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(model);
    }

    @PostMapping
    public ResponseEntity<LlmModel> createLlmModel(@RequestBody LlmModel model) {
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        boolean success = llmModelService.save(model);
        return success ? ResponseEntity.ok(model) : ResponseEntity.badRequest().build();
    }

    @PutMapping("/{modelName}")
    public ResponseEntity<LlmModel> updateLlmModel(@PathVariable String modelName, @RequestBody LlmModel model) {
        model.setModelName(modelName);
        model.setUpdatedAt(LocalDateTime.now());
        boolean success = llmModelService.updateById(model);
        return success ? ResponseEntity.ok(model) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{modelName}")
    public ResponseEntity<Void> deleteLlmModel(@PathVariable String modelName) {
        boolean success = llmModelService.removeById(modelName);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/page")
    public ResponseEntity<Page<LlmModel>> getLlmModelsByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<LlmModel> page = llmModelService.page(new Page<>(current, size));
        return ResponseEntity.ok(page);
    }
}
