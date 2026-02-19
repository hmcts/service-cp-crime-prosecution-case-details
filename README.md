# HMCTS Crime Service Spring Boot Template

This repository provides a template for building Spring Boot applications. While the initial use case was for the HMCTS API Marketplace, the template is designed to be reusable across jurisdictions and is intended as a base paved path for wider adoption.

It includes essential configurations, dependencies, and recommended practices to help teams get started quickly.

**Note:** This template is not a framework, nor is it intended to evolve into one. It simply leverages the Spring ecosystem and proven libraries from the wider engineering community.

As HMCTS services are hosted on Azure, the included dependencies reflect this. Our aim is to stay as close to the cloud as possible in order to maximise alignment with the Shared Responsibility Model and achieve optimal security and operability.

## Want to Build Your Own Path?

That’s absolutely fine — but if you do, make sure your approach meets the following baseline requirements:

* Security – All services must meet HMCTS security standards, including vulnerability scanning and least privilege access.
* Observability – Logs, metrics, and traces must be integrated into HMCTS observability stack (e.g. Azure Monitoring).
* Audit – Systems must produce audit trails that meet legal and operational requirements.
* CI/CD Integration – Pipelines must include automated testing, deployments to multiple environments, and use approved tooling (e.g. GitHub Actions or Azure DevOps).
* Compliance & Policy Alignment – Services must align with HMCTS/MoJ policies (e.g. Coding in the Open, mandatory security practices).
* Ownership & Support – Domain teams must clearly own the service, maintain a support model, and define escalation paths.

## Documentation

Further documentation can be found in the [docs](docs) directory.

### Key Documentation
- [Spring Boot v4 Upgrade Guide](docs/SpringUpgradev4.md) - Details on the Spring Boot v4 upgrade, tracing test fixes, and code refactoring improvements
- [Environment Variables Guide](docs/EnvironmentVariables.md) - Complete guide to managing environment variables with `.env` and `.envrc` files
- [JWT Filter Documentation](docs/JWTFilter.md) - JWT authentication filter configuration and usage
- [Logging Documentation](docs/Logging.md) - Logging configuration and best practices
- [Pipeline Documentation](docs/PIPELINE.md) - CI/CD pipeline configuration and deployment processes

### Prerequisites

- ☕️ **Java 25 or later**: Ensure Java is installed and available on your `PATH`.
- ⚙️ **Gradle**: [Install Gradle](https://gradle.org/install/). The project itself defines which Gradle version to use (gradle/wraper/gradle-wrapper.properties).

You can verify installation with:
```bash
java -version
gradle -v
```

## Installation

### Build
```bash
gradle build
```

`build` will run all tests.

### Tests
- `gradle test` for running unit and integration tests
- `gradle api` for running api tests


### Environment Setup for Local Builds

This project uses a two-file approach for environment variable management with `.env` and `.envrc` files. 

**Quick Setup:**
1. Install `direnv`: `brew install direnv`
2. Add to shell: `echo 'eval "$(direnv hook zsh)"' >> ~/.zshrc`
3. Allow direnv: `direnv allow`
4. Create `.env` file with your local configuration

**Server Port:** The application uses port `8082` by default. Override with:
- Environment variable: `export SERVER_PORT=8080`
- Gradle property: `./gradlew test -Pserver.port=8080`
- System property: `./gradlew test -Dserver.port=8080`

📖 **For complete setup instructions, troubleshooting, and best practices, see the [Environment Variables Guide](docs/EnvironmentVariables.md).**

## Static code analysis

Install PMD

```bash
brew install pmd
```
```bash
pmd check \
    --dir src/main/java \
    --rulesets \
    .github/pmd-ruleset.xml \
    --format html \
    -r build/reports/pmd/pmd-report.html
```

Run PMD from Gradle

```
gradle pmdTest
```

### Contribute to This Repository

Contributions are welcome! Please see the [CONTRIBUTING.md](.github/CONTRIBUTING.md) file for guidelines.

See also: [JWTFilter documentation](docs/JWTFilter.md)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
