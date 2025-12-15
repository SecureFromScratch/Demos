// src/main/java/com/example/demo/model/Task.java
package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   @Column(name = "id")
   private Long id;

   @Column(name = "name")
   private String name;

   @Column(name = "description", length = 2000)
   private String description;

   @Column(name = "created_by", updatable=false)
   private String createdBy;

   @Column(name = "assign_to")
   private String assignTo;

   @Enumerated(EnumType.STRING)
   @Column(name = "status")
   private TaskStatus status;

   
   @Column(name = "create_date", updatable=false)
   private LocalDateTime createDate;

   @PrePersist
   public void onCreate() {
      if (createDate == null) {
         createDate = LocalDateTime.now();
      }
      if (status == null) {
         status = TaskStatus.TODO;
      }
   }

   // getters and setters...
   public void setId(Long id) {
      this.id = id;
   }

   // Add this getter method
   public Long getId() {
      return id;
   }

   public String getName() {
      return name;
   }

   public String getDescription() {
      return description;
   }

   public String getCreatedBy() {
      return createdBy;
   }

   public String getAssignTo() {
      return assignTo;
   }

   public TaskStatus getStatus() {
      return status;
   }

   public LocalDateTime getCreateDate() {
      return createDate;
   }

   // Add these to your Task.java
   public void setName(String name) {
      this.name = name;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   public void setAssignTo(String assignTo) {
      this.assignTo = assignTo;
   }

   public void setStatus(TaskStatus status) {
      this.status = status;
   }

   public void setCreateDate(LocalDateTime createDate) {
      this.createDate = createDate;
   }
}