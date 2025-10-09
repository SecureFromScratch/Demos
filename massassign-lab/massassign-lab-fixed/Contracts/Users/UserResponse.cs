namespace MassAssignLab.Contracts.Users;

public class UserResponse
{
   public int Id { get; set; }
   public string Username { get; set; } = default!;
   public string Email { get; set; } = default!;
   public string FullName { get; set; } = default!;
}
