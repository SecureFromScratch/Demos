// Api/Services/RecipeService.cs
using Api.Data;
using Api.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace Api.Services
{
   public class RecipeService : IRecipeService
   {
      private readonly AppDbContext m_context;
      private readonly ILogger<RecipeService> m_logger;

      public RecipeService(AppDbContext context, ILogger<RecipeService> logger)
      {
         m_context = context;
         m_logger = logger;
      }

      public async Task<List<Recipe>> GetAllAsync()
      {
         return await m_context.Recipes
            .OrderByDescending(t => t.CreateDate)
            .ToListAsync();
      }

      public async Task<Recipe?> GetByIdAsync(long id)
      {
         return await m_context.Recipes
            .FirstOrDefaultAsync(t => t.Id == id);
      }

      public async Task<Recipe> CreateAsync(Recipe recipe, string currentUser)
      {
         recipe.CreatedBy = currentUser;
         recipe.OnCreate();

         m_context.Recipes.Add(recipe);
         await m_context.SaveChangesAsync();
         return recipe;
      }

      public async Task<Recipe?> UpdateAsync(long id, Recipe updated)
      {
         var existing = await m_context.Recipes.FirstOrDefaultAsync(t => t.Id == id);
         if (existing == null)
         {
            return null;
         }

         existing.Name = updated.Name;
         existing.Description = updated.Description;
         existing.Photo = updated.Photo;
         existing.Status = updated.Status;

         await m_context.SaveChangesAsync();
         return existing;
      }

      public async Task<bool> DeleteAsync(long id)
      {
         var existing = await m_context.Recipes.FirstOrDefaultAsync(t => t.Id == id);
         if (existing == null)
         {
            return false;
         }

         m_context.Recipes.Remove(existing);
         await m_context.SaveChangesAsync();
         return true;
      }
   }
}
