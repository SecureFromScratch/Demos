// src/main/java/com/example/demo/controller/TaskController.java
package com.example.demo.controller;

import com.example.demo.model.Task;
import com.example.demo.model.TaskStatus;
import com.example.demo.service.TaskService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/tasks")
public class TaskController {

   private final TaskService service;
   private final String tinymceApiKey;


   public TaskController(
      TaskService service,

      @Value("${tinymce.api-key:}") String tinymceApiKey) {
      this.service = service;
      this.tinymceApiKey = tinymceApiKey;

   }

   @GetMapping
   public String list(Model model) {
      model.addAttribute("tasks", service.findAll());
      return "tasks/list";
   }

   @GetMapping("/new")
   public String createForm(Model model) {
      model.addAttribute("task", new Task());
      model.addAttribute("statuses", TaskStatus.values());
      model.addAttribute("tinymceApiKey", tinymceApiKey);

      return "tasks/form";
   }

   @PostMapping
   public String create(@ModelAttribute Task task) {
      service.save(task);
      return "redirect:/tasks";
   }

   @GetMapping("/{id}/edit")
   public String editForm(@PathVariable Long id, Model model) {
      model.addAttribute("task", service.findById(id));
      model.addAttribute("statuses", TaskStatus.values());
      model.addAttribute("tinymceApiKey", tinymceApiKey);

      return "tasks/form";
   }

   @PostMapping("/{id}")
   public String update(@PathVariable Long id, @ModelAttribute Task task) {
      task.setId(id);
      service.save(task);
      return "redirect:/tasks";
   }

   @PostMapping("/{id}/delete")
   public String delete(@PathVariable Long id) {
      Task task = service.findById(id); // load from DB
      service.delete(task); // security check happens here
      return "redirect:/tasks";
   }
}
