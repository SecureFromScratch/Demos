## running in debug

```
infisical run --env=dev --projectId your-project-id -- ./gradlew bootRun --debug-jvm
```

## running in debug in specific security profile 
 ```
 infisical run --env=dev --projectId your-project-id -- ./gradlew bootRun --debug-jvm --args='--spring.profiles.active=dev'
 ```

## the corresponding configuration
   
   ```
   {
            "type": "java",
            "name": "Attach (localhost:5005)",
            "request": "attach",
            "hostName": "localhost",
            "port": 5005
    }
   ``` 