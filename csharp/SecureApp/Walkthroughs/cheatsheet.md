 ### Run the API
 
 infisical run --env=dev --projectId -your project id- -- dotnet run

### Run the bff
 
 dotnet run

 ### Run the angular
 npm install
 npm start

### Migrate data

infisical run --env=dev --projectId -your project id- -- dotnet ef migrations add AddTasks
infisical run --env=dev --projectId -your project id- -- dotnet ef database update
