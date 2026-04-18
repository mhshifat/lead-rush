package com.leadrush.task.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.task.dto.CreateTaskRequest;
import com.leadrush.task.dto.TaskResponse;
import com.leadrush.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ApiResponse<Page<TaskResponse>> listTasks(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "dueAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.success(taskService.listTasks(status, pageable));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(task));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TaskResponse> completeTask(@PathVariable UUID id) {
        return ApiResponse.success(taskService.completeTask(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ApiResponse.success("Task deleted");
    }
}
