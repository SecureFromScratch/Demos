 ### Run the API
 
 infisical run --env=dev --projectId f1d7ef76-6d48-455f-af61-cc011057c5b3 -- dotnet run

### Run the bff
 
 dotnet run

 ### Run the angular
 npm install
 npm start

### Migrate data

infisical run --env=dev --projectId f1d7ef76-6d48-455f-af61-cc011057c5b3 -- dotnet ef migrations add AddTasks
infisical run --env=dev --projectId f1d7ef76-6d48-455f-af61-cc011057c5b3 -- dotnet ef database update
