package com.devmate.task.controller;

import com.devmate.task.dto.*;
import com.devmate.task.model.TaskRecord;
import com.devmate.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }
    @GetMapping public List<TaskRecord> list() { return service.list(); }
    @GetMapping("/{id}") public TaskRecord get(@PathVariable String id) { return service.get(id); }
    @PostMapping public TaskRecord propose(@Valid @RequestBody PlanRequest request) { return service.propose(request); }
    @PostMapping("/{id}/approve") public TaskRecord approve(@PathVariable String id, @Valid @RequestBody ApprovalRequest request) { return service.approve(id, request); }
    @PostMapping("/{id}/reject") public TaskRecord reject(@PathVariable String id) { return service.reject(id); }
    @PostMapping("/{id}/revoke") public TaskRecord revoke(@PathVariable String id) { return service.revoke(id); }
    @PostMapping("/{id}/execute") public TaskRecord execute(@PathVariable String id) { return service.execute(id); }
    @PostMapping("/{id}/cancel") public TaskRecord cancel(@PathVariable String id) { return service.cancel(id); }
    @GetMapping("/{id}/logs") public List<Map<String, Object>> logs(@PathVariable String id, @RequestParam(defaultValue = "0") long after) { return service.logs(id, after); }
}
