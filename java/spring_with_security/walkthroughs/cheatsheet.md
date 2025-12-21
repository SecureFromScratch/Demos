## running in debug
infisical run --env=dev --projectId 4027f1c4-9559-408f-8538-407a392d1479 -- ./gradlew bootRun --debug-jvm

## the corresponding configuration
   {
            "type": "java",
            "name": "Attach (localhost:5005)",
            "request": "attach",
            "hostName": "localhost",
            "port": 5005
        }