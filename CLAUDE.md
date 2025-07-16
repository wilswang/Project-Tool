# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java-based White Label Inserter tool that processes JSON configuration files to generate SQL scripts and Java/JS code for white label site configurations. The tool supports both regular white label sites and API-based white label configurations.

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
```bash
# Run the main application (after building)
java -jar target/Project-Tool-1.0.4-jar-with-dependencies.jar A

# Run JsonToFile2 (Option A)
java MainSelector A

# Run UrlChecker (Option B)  
java MainSelector B
```

## Architecture Overview

### Core Components

1. **MainSelector** - Entry point that routes to different tools based on command line arguments
   - Option A: JsonToFile2 (White Label Generator)
   - Option B: UrlChecker (Domain Checker)

2. **JsonToFile2** - Main white label processing engine
   - Reads `temp.json` configuration file
   - Generates SQL scripts for DB-01 and DB-41 environments
   - Creates Java enum and JavaScript constant files
   - Handles both regular and API white label configurations
   - Supports template-based code generation

3. **UrlChecker** - Domain connectivity checker
   - Reads `checkDomain.json` configuration
   - Tests HTTP/HTTPS connectivity to domain lists
   - Configurable timeouts and subdomain support

4. **TemplateEngine** - Template processing utility
   - Handles placeholder replacement in template files
   - Supports file-based template processing
   - Manages output file generation

### Data Model (dto package)

- **WhiteLabel** - Main configuration object with validation
- **ApiWalletInfo** - API wallet specific configuration
- **GroupInfo** - Group-specific IP and domain settings

### Key Dependencies

- Jackson (JSON processing)
- Lombok (code generation)
- Hibernate Validator (validation)
- Apache Commons Lang3 & Collections4
- JUnit 4 (testing)

## Input Files

### temp.json (White Label Configuration)
Primary input file for white label generation containing:
- Site configuration (name, value, host)
- API wallet information (certificates, groups)
- Group settings (IPs, domains, backup configurations)

### checkDomain.json (Domain Checker Configuration)
Configuration for domain connectivity testing:
- Domain list to check
- Protocol settings (HTTP/HTTPS)
- Subdomain configuration
- Timeout settings

## Output Structure

Generated files are placed in `./result/` directory:
- SQL scripts: `SACRIC-{ticketNo}-DB-01.sql`, `SACRIC-{ticketNo}-DB-41.sql`
- Java code files inserted into existing source files
- Template-based output files

## Template System

The tool uses template files located in `./template/` directory for:
- Database schema generation
- Java enum generation
- JavaScript constant generation
- Domain type configurations

Templates use `{$variable}` placeholders for dynamic content replacement.

## Validation

The WhiteLabel class includes comprehensive validation:
- Bean validation annotations (@NotBlank, @NotNull, @Min)
- Custom conditional validation logic
- Supports different validation rules for API vs regular white labels