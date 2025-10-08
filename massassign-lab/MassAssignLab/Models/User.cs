namespace MassAssignLab.Models;

public class User
{
   public int Id { get; set; }
   public string Username { get; set; } = default!;
   public string Email { get; set; } = default!;
   public string FullName { get; set; } = default!;
   
   public string Role { get; set; } = "user";
   public bool IsActive { get; set; } = true;
}
