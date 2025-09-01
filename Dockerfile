# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw clean package -DskipTests

# Create a non-root user
RUN addgroup --system javauser && adduser --system --ingroup javauser javauser

# Change ownership of the jar file
RUN chown javauser:javauser target/*.jar

# Switch to non-root user
USER javauser

# Expose port
EXPOSE 1402

# Run the application
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "target/firewallweb-0.0.1-SNAPSHOT.jar"]
