# Use the official OpenJDK 21 Slim image (Debian-based)
FROM openjdk:21-slim

# Install Node.js, Python, GCC, and other necessary utilities
RUN apt-get update && apt-get install -y \
    nodejs \
    npm \
    python3 \
    python3-pip \
    gcc \
    g++ \
    make \
    && rm -rf /var/lib/apt/lists/*

# Ensure python command is available
RUN ln -s /usr/bin/python3 /usr/bin/python

# Set the working directory inside the container
WORKDIR /compilers

# Copy necessary files for Maven wrapper and project setup
COPY mvnw.cmd .
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Give execute permission to Maven wrapper on Linux
RUN chmod +x mvnw

# Download Maven dependencies to reduce build time later
RUN ./mvnw dependency:go-offline

# Copy the application source code
COPY src src

# Build the application
RUN ./mvnw package -DskipTests

# Create a non-root user and switch to it
RUN useradd -m safeuser
USER safeuser

# Expose the port your application runs on
EXPOSE 9999

# Set environment variables for JVM memory settings
ENV JAVA_OPTS="-Xms512m -Xmx2g"

# Specify the command to run your application
# Use the shell form of ENTRYPOINT to ensure that environment variables are expanded correctly
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar target/compilers.jar"]