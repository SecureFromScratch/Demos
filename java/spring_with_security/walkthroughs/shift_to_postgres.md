# Spring Boot with Infisical Secrets Management Tutorial

## 1. Run Infisical Self-Hosted

Clone and set up Infisical:

```bash
git clone https://github.com/Infisical/infisical.git
cd infisical
cp .env.example .env
```

### Configure Infisical

1. **Change the port** in `docker-compose.prod.yml`:

```yaml
ports:
  - 9099:8080
```

2. **Generate and set encryption key** in `.env`:

```bash
openssl rand -base64 24
```

Copy the output and paste it into your `.env` file as the encryption key.

3. **Start Infisical**:

```bash
docker compose -f docker-compose.prod.yml up -d
```

4. **Set up your project**:
   - Open `http://localhost:9099` in your browser
   - Create a new account
   - Create a workspace/project (e.g., `spring-app`)
   - Create an environment: `dev`

---

## 2. Create Database Secrets in Infisical

In the `dev` environment of your project, add these secrets:

* `DB_USERNAME` = `appuser`
* `DB_PASSWORD` = `your_secure_password`
* `DB_URL` = `jdbc:postgresql://localhost:5433/appdb`

> **Note:** We use port `5433` to avoid conflicts with other PostgreSQL instances.

---

## 3. Install and Authenticate Infisical CLI

### Install the CLI

Follow the installation guide for your OS from [Infisical CLI docs](https://infisical.com/docs/cli/overview).

### Login

```bash
infisical login
```

Follow the prompts to authenticate. Note your **Project ID** for later use.

---

## 4. Configure Spring Boot to Use Environment Variables

In `src/main/resources/application.properties`:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
```

**Important:** No hardcoded passwords—only placeholders that will be injected by Infisical.

---

## 5. Create Docker Compose for PostgreSQL

Create `docker-compose.yml` in your Spring Boot project root:

```yaml
version: "3.9"

services:
  postgres:
    image: postgres:18
    container_name: spring-security-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: appdb
    ports:
      - "5433:5432"   # Host port 5433 to avoid conflicts
    volumes:
      - pgdata_spring:/var/lib/postgresql

volumes:
  pgdata_spring:
```

> **Note:** The volume mount uses `/var/lib/postgresql` (not `/var/lib/postgresql/data`) for PostgreSQL 18+ compatibility.

---

## 6. Start PostgreSQL with Secrets from Infisical

From your Spring Boot project folder:

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- docker compose up -d
```

**What happens:**
- Infisical fetches `DB_USERNAME` and `DB_PASSWORD` from your `dev` environment
- Injects them as environment variables
- Docker Compose uses them to initialize PostgreSQL

**Verify it's running:**

```bash
docker ps
docker logs spring-security-postgres
```

You should see: `database system is ready to accept connections`

---

## 7. Run Spring Boot Application

### Standard Run

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- ./gradlew bootRun
```

Infisical injects `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` into your Spring Boot process.

### What You've Achieved

✅ No secrets in Git  
✅ No secrets in command line history  
✅ All credentials centralized in Infisical  
✅ Easy secret rotation without code changes

---

## 8. Debug Mode in VS Code

### Attach to Running Process (Recommended)

1. **Create `.vscode/launch.json`** in your project root:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Attach to Gradle",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005
    }
  ]
}
```

2. **Start your app in debug mode:**

```bash
infisical run --env=dev --projectId <YOUR_PROJECT_ID> -- ./gradlew bootRun --debug-jvm
```

3. **Attach debugger:**
   - Press `F5` in VS Code
   - Select "Attach to Gradle"
   - Set breakpoints and debug!


---

## Troubleshooting

### PostgreSQL Container Keeps Restarting

```bash
# Check logs
docker logs spring-security-postgres

# If you see volume errors, remove old volumes:
docker compose down -v
docker volume rm <volume_name>
```

### Connection Refused

- Ensure PostgreSQL is running: `docker ps`
- Wait 5-10 seconds after starting for PostgreSQL to be ready
- Verify port in `DB_URL` matches docker-compose port mapping (5433)

### Port Already in Use

If port 5433 is taken:

```bash
# Check what's using the port
lsof -i :5433

# Either stop that service or change the port in docker-compose.yml


---

