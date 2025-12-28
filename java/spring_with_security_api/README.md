# SecureApp API - Java Spring Boot

## Prerequisites

- Java 17 or higher
- Docker & Docker Compose
- Gradle (or use the wrapper `./gradlew`)

## Quick Start

### 1. Start the Database

```bash
# Start PostgreSQL and PgAdmin
docker-compose up -d

# Check if containers are running
docker-compose ps

# View logs
docker-compose logs -f postgres
```

### 2. Configure Environment Variables

```bash
# Copy the example env file
cp .env.example .env

# Edit .env and update JWT_SECRET with a secure random string
# Generate a secure JWT secret (at least 256 bits):
openssl rand -base64 32
```

### 3. Run the Application

```bash
# Using Gradle wrapper (recommended)
./gradlew bootRun

# Or build and run the JAR
./gradlew build
java -jar build/libs/api-0.0.1-SNAPSHOT.jar
```

The API will start on `http://localhost:8080`

### 4. Access Swagger UI

Open your browser to: `http://localhost:8080/swagger-ui.html`

### 5. Access PgAdmin (Optional)

- URL: `http://localhost:5050`
- Email: `admin@example.com`
- Password: `admin`

**To connect to PostgreSQL in PgAdmin:**
1. Right-click "Servers" → "Register" → "Server"
2. General tab: Name = "API Database"
3. Connection tab:
   - Host: `postgres` (or `localhost` if outside Docker)
   - Port: `5432`
   - Database: `myappdb`
   - Username: `appuser`
   - Password: `apppassword`

## Database Management

```bash
# Stop containers
docker-compose down

# Stop and remove volumes (deletes all data)
docker-compose down -v

# Restart containers
docker-compose restart

# View PostgreSQL logs
docker-compose logs -f postgres

# Connect to PostgreSQL CLI
docker-compose exec postgres psql -U appuser -d myappdb
```

## Useful PostgreSQL Commands

```sql
-- List all tables
\dt

-- Describe a table
\d users

-- View all users
SELECT * FROM users;

-- Quit
\q
```

## API Endpoints

### Authentication
- `GET /api/account/is-first-user` - Check if first user exists
- `POST /api/account/setup` - Register first admin
- `POST /api/account/register` - Register new user
- `POST /api/account/login` - Login
- `POST /api/account/logout` - Logout
- `GET /api/account/me` - Get current user (requires auth)

### Example: First User Setup

```bash
# 1. Check if first user
curl http://localhost:8080/api/account/is-first-user

# 2. Setup first admin
curl -X POST http://localhost:8080/api/account/setup \
  -H "Content-Type: application/json" \
  -d '{"userName":"admin","password":"Admin123!@#"}'

# 3. Login
curl -X POST http://localhost:8080/api/account/login \
  -H "Content-Type: application/json" \
  -d '{"userName":"admin","password":"Admin123!@#"}'

# 4. Use the token from login response
curl http://localhost:8080/api/account/me \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

## Project Structure

```
src/
├── main/
│   ├── java/com/example/api/
│   │   ├── Application.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   └── JwtAuthenticationFilter.java
│   │   ├── controllers/
│   │   │   └── AccountController.java
│   │   ├── services/
│   │   │   ├── IUserService.java
│   │   │   └── UserService.java
│   │   ├── models/
│   │   │   └── AppUser.java
│   │   └── data/
│   │       └── UserRepository.java
│   └── resources/
│       └── application.properties
└── test/
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | Database host | `localhost` |
| `DB_PORT` | Database port | `5432` |
| `DB_NAME` | Database name | `myappdb` |
| `DB_USER` | Database user | `appuser` |
| `DB_PASSWORD` | Database password | `apppassword` |
| `JWT_SECRET` | JWT signing key | **Required** |
| `JWT_ISSUER` | JWT issuer | `secureapp-api` |
| `JWT_AUDIENCE` | JWT audience | `secureapp-client` |

## Troubleshooting

### Port 5432 already in use
```bash
# Check what's using the port
lsof -i :5432

# Stop existing PostgreSQL
sudo systemctl stop postgresql
```

### Database connection refused
```bash
# Check if container is running
docker-compose ps

# Check container logs
docker-compose logs postgres
```

### JWT Secret not set
```bash
# Make sure .env file exists and contains JWT_SECRET
cat .env | grep JWT_SECRET
```