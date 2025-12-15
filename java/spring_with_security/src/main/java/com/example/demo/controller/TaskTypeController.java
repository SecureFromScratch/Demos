package com.example.demo.controller;

import com.example.demo.model.TaskType;
import com.example.demo.model.User;
import com.example.demo.repository.TaskTypeRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/task-types")
public class TaskTypeController {
    
    @Autowired
    private TaskTypeRepository taskTypeRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    // View all task types - EVERYONE can access
    @GetMapping
    public String viewTaskTypes(Model model, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<TaskType> taskTypes = taskTypeRepository.findAll();
        
        // Check if user is from project_management department
        boolean canEdit = "project_management".equalsIgnoreCase(currentUser.getDepartment());
        
        model.addAttribute("taskTypes", taskTypes);
        model.addAttribute("canEdit", canEdit);
        model.addAttribute("currentUser", currentUser);
        
        return "task-types";
    }
    
    // Show create form - Only project_management
    @GetMapping("/create")
    public String showCreateForm(Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (!isProjectManagement(authentication)) {
            redirectAttributes.addFlashAttribute("error", "Access denied. Only Project Management can create task types.");
            return "redirect:/task-types";
        }
        
        model.addAttribute("taskType", new TaskType());
        return "task-type-form";
    }
    
    // Create task type - Only project_management
    @PostMapping("/create")
    public String createTaskType(@ModelAttribute TaskType taskType, 
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        if (!isProjectManagement(authentication)) {
            redirectAttributes.addFlashAttribute("error", "Access denied. Only Project Management can create task types.");
            return "redirect:/task-types";
        }
        
        // Check if name already exists
        if (taskTypeRepository.existsByName(taskType.getName())) {
            redirectAttributes.addFlashAttribute("error", "Task type with this name already exists.");
            return "redirect:/task-types/create";
        }
        
        taskTypeRepository.save(taskType);
        redirectAttributes.addFlashAttribute("success", "Task type created successfully!");
        return "redirect:/task-types";
    }
    
    // Show edit form - Only project_management
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, 
                              Model model, 
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        if (!isProjectManagement(authentication)) {
            redirectAttributes.addFlashAttribute("error", "Access denied. Only Project Management can edit task types.");
            return "redirect:/task-types";
        }
        
        TaskType taskType = taskTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task type not found"));
        
        model.addAttribute("taskType", taskType);
        model.addAttribute("isEdit", true);
        return "task-type-form";
    }
    
    // Update task type - Only project_management
    @PostMapping("/edit/{id}")
    public String updateTaskType(@PathVariable Long id,
                                @ModelAttribute TaskType updatedTaskType,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        if (!isProjectManagement(authentication)) {
            redirectAttributes.addFlashAttribute("error", "Access denied. Only Project Management can edit task types.");
            return "redirect:/task-types";
        }
        
        TaskType taskType = taskTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task type not found"));
        
        // Check if new name conflicts with existing (excluding current)
        if (!taskType.getName().equals(updatedTaskType.getName()) && 
            taskTypeRepository.existsByName(updatedTaskType.getName())) {
            redirectAttributes.addFlashAttribute("error", "Task type with this name already exists.");
            return "redirect:/task-types/edit/" + id;
        }
        
        taskType.setName(updatedTaskType.getName());
        taskType.setDescription(updatedTaskType.getDescription());
        taskTypeRepository.save(taskType);
        
        redirectAttributes.addFlashAttribute("success", "Task type updated successfully!");
        return "redirect:/task-types";
    }
    
    // Delete task type - Only project_management
    @PostMapping("/delete/{id}")
    public String deleteTaskType(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        if (!isProjectManagement(authentication)) {
            redirectAttributes.addFlashAttribute("error", "Access denied. Only Project Management can delete task types.");
            return "redirect:/task-types";
        }
        
        taskTypeRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Task type deleted successfully!");
        return "redirect:/task-types";
    }
    
    // Helper method to check if user is from project_management
    private boolean isProjectManagement(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return "project_management".equalsIgnoreCase(user.getDepartment());
    }
}


