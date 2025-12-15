using Microsoft.EntityFrameworkCore;

using Api.Models;
using TaskEntity = Api.Models.Task;


namespace Api.Data;

public class AppDbContext : DbContext
{
   public AppDbContext(DbContextOptions<AppDbContext> options)
      : base(options)
   {
   }

   public DbSet<AppUser> Users => Set<AppUser>();
   public DbSet<TaskEntity> Tasks => Set<TaskEntity>();

   public DbSet<Recipe> Recipes => Set<Recipe>();


   protected override void OnModelCreating(ModelBuilder modelBuilder)
   {
      base.OnModelCreating(modelBuilder);

      var task = modelBuilder.Entity<TaskEntity>();

      task.Property(t => t.Status)
         .HasConversion<string>()
         .IsRequired();

      task.Property(t => t.CreateDate)
         .IsRequired();

      task.Property(t => t.Name)
         .HasMaxLength(200)
         .IsRequired();

      task.Property(t => t.Description)
         .HasMaxLength(2000);

      task.Property(t => t.CreatedBy)
         .HasMaxLength(100)
         .IsRequired();

      task.Property(t => t.AssignTo)
         .HasMaxLength(100);


      var recipe = modelBuilder.Entity<Recipe>();

      recipe.Property(t => t.Status)
         .HasConversion<string>()
         .IsRequired();

      recipe.Property(t => t.CreateDate)
         .IsRequired();

      recipe.Property(t => t.Name)
         .HasMaxLength(200)
         .IsRequired();

      recipe.Property(t => t.Description)
         .HasMaxLength(2000);

      task.Property(t => t.CreatedBy)
         .HasMaxLength(100)
         .IsRequired();

      recipe.Property(t => t.Photo);
         
   }
}

