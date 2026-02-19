# Environment Variable Management

This document explains how to manage environment variables in this Spring Boot project using `.env` and `.envrc` files.

## Overview

This project uses a two-file approach for environment variable management:

**📋 `.env` File (Data Storage)**
- Contains simple key-value pairs: `KEY=value`
- Usually **gitignored** (contains sensitive data)
- Static data only - no shell commands
- Loaded by `dotenv` command in `.envrc`

**🔧 `.envrc` File (Environment Setup Script)**
- Shell script that sets up the environment
- Usually **committed to git** (contains safe defaults)
- Can execute commands, use variables, and provide logic
- Loaded directly by direnv

## How They Work Together

1. `direnv` loads `.envrc` when you enter the project directory
2. `.envrc` loads `.env` via `dotenv` command
3. `.envrc` processes and exports environment variables
4. Your shell environment is automatically configured

## File Examples

### Example `.env` file:
```bash
# Simple key-value pairs (usually gitignored)
SERVER_PORT=8082
DATABASE_PASSWORD=secret123
API_KEY=abc123xyz
JWT_SECRET=my-secret-key

# Database configuration
APP_NAME_DATASOURCE_URL=jdbc:postgresql://localhost:55432/appdb
APP_NAME_DATASOURCE_USERNAME=app
APP_NAME_DATASOURCE_PASSWORD=app
APP_NAME_DB_POOL_SIZE=20
APP_NAME_DB_MIN_IDLE=5
```

### Example `.envrc` file:
```bash
# Load .env file
dotenv

# Use .env values or provide defaults
export SERVER_PORT=${SERVER_PORT:-8082}
export DATABASE_PASSWORD=${DATABASE_PASSWORD:-default-password}

# Database configuration
export APP_NAME_DATASOURCE_URL=${APP_NAME_DATASOURCE_URL:-jdbc:postgresql://localhost:55432/appdb}
export APP_NAME_DATASOURCE_USERNAME=${APP_NAME_DATASOURCE_USERNAME:-app}
export APP_NAME_DATASOURCE_PASSWORD=${APP_NAME_DATASOURCE_PASSWORD:-app}
export APP_NAME_DB_POOL_SIZE=${APP_NAME_DB_POOL_SIZE:-20}
export APP_NAME_DB_MIN_IDLE=${APP_NAME_DB_MIN_IDLE:-5}

# Add computed values
export MANAGEMENT_SERVER_PORT=${MANAGEMENT_SERVER_PORT:-${SERVER_PORT}}

# Add new variables
export JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+AlwaysActAsServerClassMachine"
export CLUSTER_NAME="local"
export REGION="local"

# Optional: Set JWT secret for local development
export JWT_SECRET_KEY="it-must-be-a-string-secret-at-least-256-bits-long"

# Optional: Set Azure Application Insights key for local development
export AZURE_APPLICATION_INSIGHTS_INSTRUMENTATION_KEY="00000000-0000-0000-0000-000000000000"

# Optional: Set tracing configuration
export TRACING_SAMPLER_PROBABILITY="1.0"
export OTEL_TRACES_URL="http://localhost:4318/v1/traces"
export OTEL_METRICS_ENABLED="false"
export OTEL_METRICS_URL="http://localhost:4318/v1/metrics"

# Optional: Set virtual threads (Java 21 feature)
export VIRTUAL_THREADS="false"

# Execute setup commands
echo "🔧 Environment loaded for Spring Boot service"
echo "   Server Port: ${SERVER_PORT}"
echo "   Management Port: ${MANAGEMENT_SERVER_PORT}"
echo "   Java Options: ${JAVA_OPTS}"
echo "   Virtual Threads: ${VIRTUAL_THREADS}"
```

## Setup Instructions

### Prerequisites

Install `direnv` on macOS:
```bash
brew install direnv
```

### Shell Configuration

Hook direnv into your shell:

**For zsh:**
```bash
echo 'eval "$(direnv hook zsh)"' >> ~/.zshrc
source ~/.zshrc
```

**For bash:**
```bash
echo 'eval "$(direnv hook bash)"' >> ~/.bash_profile
source ~/.bash_profile
```

### Project Setup

1. **Allow direnv to load:**
   ```bash
   direnv allow
   ```

2. **Create your `.env` file (if it doesn't exist):**
   ```bash
   # Create .env file with your local configuration
   cat > .env << EOF
   SERVER_PORT=8082
   # Add other environment variables as needed
   EOF
   ```

3. **Verify setup:**
   ```bash
   # Check if direnv is working
   direnv status
   
   # Test environment loading
   direnv exec . env | grep SERVER_PORT
   
   # See what .envrc does
   cat .envrc
   ```

## Database Configuration

The application connects to PostgreSQL by default. You can configure the database connection using the following environment variables:

### Database Environment Variables

| Variable | Default Value | Description |
|----------|---------------|-------------|
| `APP_NAME_DATASOURCE_URL` | `jdbc:postgresql://localhost:55432/appdb` | JDBC URL for database connection |
| `APP_NAME_DATASOURCE_USERNAME` | `app` | Database username |
| `APP_NAME_DATASOURCE_PASSWORD` | `app` | Database password |
| `APP_NAME_DB_POOL_SIZE` | `20` | Maximum number of connections in the pool |
| `APP_NAME_DB_MIN_IDLE` | `5` | Minimum number of idle connections |

### Database Configuration Examples

**Local Development (PostgreSQL on localhost):**
```bash
export APP_NAME_DATASOURCE_URL=jdbc:postgresql://localhost:55432/appdb
export APP_NAME_DATASOURCE_USERNAME=app
export APP_NAME_DATASOURCE_PASSWORD=app
```

**Docker Compose (PostgreSQL container):**
```bash
export APP_NAME_DATASOURCE_URL=jdbc:postgresql://db:5432/appdb
export APP_NAME_DATASOURCE_USERNAME=app
export APP_NAME_DATASOURCE_PASSWORD=app
```

**Production (External database):**
```bash
export APP_NAME_DATASOURCE_URL=jdbc:postgresql://prod-db.example.com:5432/appdb
export APP_NAME_DATASOURCE_USERNAME=prod_user
export APP_NAME_DATASOURCE_PASSWORD=secure_password
```

## Server Port Configuration

The application uses port `8082` by default. You can override this using any of the following methods:

### Configuration Methods

**🎯 Recommended: Using direnv (automatic environment loading)**
```bash
# Edit .envrc to change the default port
# The environment will be automatically loaded when you enter the project directory
./gradlew integration
```

**🔧 Manual environment variable override:**
```bash
export SERVER_PORT=8080
./gradlew integration
```

**⚙️ Gradle property override:**
```bash
./gradlew integration -Pserver.port=8080
```

**🔨 System property override:**
```bash
./gradlew integration -Dserver.port=8080
```

### Priority Order (highest to lowest):
1. System property (`-Dserver.port=8080`)
2. Gradle property (`-Pserver.port=8080`)
3. Environment variable (`SERVER_PORT=8080`)
4. `.env` file value
5. `.envrc` default value (`8082`)

## Troubleshooting

### Common Issues and Solutions

**Issue: `direnv: command not found`**
```bash
# Solution: Install direnv
brew install direnv

# Add to shell profile
echo 'eval "$(direnv hook zsh)"' >> ~/.zshrc
source ~/.zshrc
```

**Issue: `direnv: .envrc is blocked`**
```bash
# Solution: Allow direnv to load the file
direnv allow
```

**Issue: `(eval):1: SERVER_PORT: parameter not set`**
```bash
# Solution: Check if .env file exists and has the variable
cat .env
# If missing, create it:
echo "SERVER_PORT=8082" > .env
```

**Issue: Environment variables not loading**
```bash
# Solution: Check direnv status
direnv status

# Manually reload
direnv reload

# Test environment loading
direnv exec . env | grep SERVER_PORT
```

**Issue: Port already in use**
```bash
# Solution: Find and kill process using port 8082
lsof -ti:8082 | xargs kill -9

# Or use a different port
export SERVER_PORT=8080
./gradlew integration
```

**Issue: `.env` file is protected/read-only**
```bash
# Solution: Check file permissions
ls -la .env

# If needed, make it writable
chmod 644 .env

# Or create a new one
rm .env
echo "SERVER_PORT=8082" > .env
```

**Issue: Database connection fails**
```bash
# Solution: Check database environment variables
env | grep APP_NAME_DATASOURCE

# Verify database is running
docker ps | grep postgres

# Test database connection
psql -h localhost -p 55432 -U app -d appdb

# Check application logs for connection errors
./gradlew integration --info
```

**Issue: Wrong database hostname in Docker**
```bash
# Solution: Use correct hostname for Docker Compose
# For local development:
export APP_NAME_DATASOURCE_URL=jdbc:postgresql://localhost:55432/appdb

# For Docker Compose:
export APP_NAME_DATASOURCE_URL=jdbc:postgresql://db:5432/appdb
```

## Best Practices

### Security
- **Never commit sensitive data** to `.envrc` (it's usually committed to git)
- **Always gitignore** your `.env` file
- **Use safe defaults** in `.envrc` for development
- **Override with real values** in `.env` for local development

### Organization
- **Group related variables** together in `.env`
- **Use descriptive names** for environment variables
- **Document variable purposes** in comments
- **Keep `.envrc` focused** on environment setup logic

### Development Workflow
- **Test environment loading** after making changes
- **Use `direnv reload`** to test changes without leaving the directory
- **Verify variables** with `env | grep VARIABLE_NAME`
- **Check direnv status** if things aren't working

## Integration with Spring Boot

The environment variables are automatically picked up by Spring Boot through:

1. **System properties** (set by direnv)
2. **Environment variables** (exported by `.envrc`)
3. **Application properties** (referenced in `application.yaml`)

Example in `application.yaml`:
```yaml
server:
  port: ${SERVER_PORT:8082}

management:
  server:
    port: ${MANAGEMENT_SERVER_PORT:${SERVER_PORT:8082}}

spring:
  datasource:
    url: ${APP_NAME_DATASOURCE_URL:jdbc:postgresql://localhost:55432/appdb}
    username: ${APP_NAME_DATASOURCE_USERNAME:app}
    password: ${APP_NAME_DATASOURCE_PASSWORD:app}
    hikari:
      maximum-pool-size: ${APP_NAME_DB_POOL_SIZE:20}
      minimum-idle: ${APP_NAME_DB_MIN_IDLE:5}
```

This ensures that the environment variables set by direnv are properly used by the Spring Boot application for both server configuration and database connectivity.
