# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java-based tool that processes JSON configuration files to generate SQL scripts and Java/JS code for white label site configurations. The tool supports both regular white label sites and API-based white label configurations.

## Build and Development Commands

### Build Commands
```bash
# Compile the project
mvn compile

# Package the project (creates fat JAR with dependencies)
mvn package

# Clean build artifacts
mvn clean

# Run tests
mvn test

# Run a single test
mvn test -Dtest=WhiteLabelTest

# Run a specific test method
mvn test -Dtest=WhiteLabelTest#testLombokGetter_isSqlOnly
```

### Running the Application

**Preferred method (using shell scripts):**
```bash
# From project root, navigate to scripts location
cd src/main/resources

# Windows
project-tool.bat A    # White Label Generator
project-tool.bat B    # Domain Checker

# Mac/Linux (make executable first time)
chmod +x project-tool.sh
./project-tool.sh A   # White Label Generator  
./project-tool.sh B   # Domain Checker
```

**Alternative method (direct execution):**
```bash
# Run from target/ directory after mvn package
java -jar target/Project-Tool-1.0.4-jar-with-dependencies.jar A

# Run directly with classpath
java MainSelector A   # White Label Generator
java MainSelector B   # Domain Checker
```

## Architecture Overview

### Core Components

1. **MainSelector** - Entry point that routes to different tools based on command line arguments
   - Option A: WhiteLabelTool (White Label Generator)
   - Option B: UrlChecker (Domain Checker)

2. **WhiteLabelTool** - Main white label processing engine
   - Reads `whiteLabelConfig.json` configuration file
   - Generates SQL scripts for DB-01 and DB-41 environments
   - Creates Java enum and JavaScript constant files
   - Handles both regular and API white label configurations
   - Uses template-based code generation via TemplateEngine

3. **UrlChecker** - Domain connectivity checker
   - Reads `checkDomain.json` configuration
   - Tests HTTP/HTTPS connectivity to domain lists
   - Configurable timeouts and subdomain support
   - Uses DomainCheckInfo DTO for structured configuration

4. **TemplateEngine** (util package) - Template processing utility
   - Handles placeholder replacement in template files
   - Supports file-based template processing
   - Manages output file generation
   - Uses `{$variable}` placeholder syntax

### Data Model (dto package)

- **WhiteLabel** - Main configuration object with comprehensive validation
- **ApiWalletInfo** - API wallet specific configuration
- **GroupInfo** - Group-specific IP and domain settings
- **DomainCheckInfo** - Domain checker configuration with timeout validation

### Key Dependencies

- Jackson (JSON processing)
- Lombok (code generation)
- Hibernate Validator (validation)
- Apache Commons Lang3 & Collections4
- JUnit 4 (testing)

## Input Files

### whiteLabelConfig.json (White Label Configuration)
Primary input file for white label generation containing:
- Site configuration (name, value, host)
- API wallet information (certificates, groups)
- Group settings (IPs, domains, backup configurations)
- Boolean flags for sqlOnly and apiWhiteLabel modes

### checkDomain.json (Domain Checker Configuration)
Configuration for domain connectivity testing:
- Domain list to check
- Protocol settings (HTTP/HTTPS)
- Subdomain configuration
- Timeout settings (15s-2min range enforced)

## Output Structure

Generated files are placed in `./result/` directory:
- SQL scripts: `SACRIC-{ticketNo}-DB-01.sql`, `SACRIC-{ticketNo}-DB-41.sql`
- Java code files inserted into existing source files
- Template-based output files

## Template System

The tool uses template files located in `./template/` directory for:
- Database schema generation (NewSite-DB-01/41-template.txt)
- API wallet templates (ApiWallet-DB-01/41-template.txt)
- Java enum generation
- JavaScript constant generation
- Domain type configurations

Templates use `{$variable}` placeholders for dynamic content replacement via TemplateEngine.

## Shell Scripts Setup

The project includes convenience scripts in `src/main/resources/`:
- `project-tool.bat` (Windows)
- `project-tool.sh` (Mac/Linux)

**Setup requirements:**
1. Run `mvn package` to generate JAR file
2. Copy `Project-Tool-1.0.4-jar-with-dependencies.jar` from `target/` to `src/main/resources/`
3. Place input JSON files in `src/main/resources/`
4. Make scripts executable: `chmod +x project-tool.sh` (Mac/Linux)

**Script features:**
- Auto-checks for JAR file existence
- Auto-creates `result/` output directory
- Handles directory navigation automatically

## Validation

### WhiteLabel Configuration
- Bean validation annotations (@NotBlank, @NotNull, @Min)
- Custom conditional validation logic
- API mode requires apiWalletInfo when apiWhiteLabel=true
- New group creation requires groupInfo when newGroup=true

### DomainCheckInfo Configuration  
- Required domainList validation
- Timeout constraints (15000-120000ms)
- Built-in validate() method with descriptive error messages
- Exits gracefully on validation failures

## Development Workflow

1. **Make changes** to Java source files
2. **Test changes** with `mvn test`
3. **Build project** with `mvn package`
4. **Copy JAR** to `src/main/resources/` if using shell scripts
5. **Prepare JSON** input files in correct location
6. **Run tool** using preferred execution method

# Project Structure

```
/tools/
  /<tool-name>/
    - ToolMain.java            # Entry point
    - ToolConfig.java          # JSON config class with @Valid annotations
    - ToolService.java         # Core logic
    - ToolUtils.java           # Optional helpers
    - ToolMainTest.java        # JUnit test class

/scripts/
  - run-tool.sh                # Unix execution script
  - run-tool.bat               # Windows execution script

/resources/
  - sample-config.json         # Example JSON config
  - schema.md                  # Description of config schema
```

# Testing Guidelines

- All tools must include unit tests using **JUnit 5**
- Use **Mockito** to mock dependencies when needed
- Assertions should be written with **AssertJ** or standard `assertEquals`
- Test files should follow the naming convention: `XxxToolTest.java`
- Place test classes under `src/test/java/` matching the package structure

# Common Dependencies

- Jackson (`com.fasterxml.jackson.databind.ObjectMapper`) for JSON processing
- Hibernate Validator (`javax.validation.constraints.*`) for config validation
- Spring Boot (`org.springframework.boot.*`) for application framework and lifecycle

# JSON Validation Example

```java
public class ToolConfig {
    @NotBlank
    private String inputFile;

    @Min(1)
    private int threadCount = 1;

    @NotNull
    private List<String> targets;
}
```