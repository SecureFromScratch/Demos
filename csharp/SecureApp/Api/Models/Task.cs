// File: Api/Models/Task.cs
using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Api.Models
{
   [Table("Tasks")]
   public class Task
   {
      [Key]
      [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
      public long Id { get; set; }

      [Required]
      [MaxLength(200)]
      public string Name { get; set; } = string.Empty;

      [MaxLength(2000)]
      public string? Description { get; set; }

      [Required]
      [MaxLength(100)]
      public string CreatedBy { get; set; } = string.Empty;

      [MaxLength(100)]
      public string? AssignTo { get; set; }

      [Required]
      public TaskStatus Status { get; set; } = TaskStatus.Todo;

      [Required]
      public DateTime CreateDate { get; set; }

      public void OnCreate()
      {
         if (CreateDate == default)
         {
            CreateDate = DateTime.UtcNow;
         }

         if (Status == default)
         {
            Status = TaskStatus.Todo;
         }
      }
   }
}
