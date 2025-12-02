// src/main/java/com/example/demo/service/TaskService.java
package com.example.demo.service;

import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

   private final TaskRepository repo;

   public TaskService(TaskRepository repo) {
      this.repo = repo;
   }

   public List<Task> findAll() {
      return repo.findAll();
   }

   public Task findById(Long id) {
      return repo.findById(id).orElseThrow();
   }

   public Task save(Task task) {
      return repo.save(task);
   }

   public void delete(Long id) {
      repo.deleteById(id);
   }
}
