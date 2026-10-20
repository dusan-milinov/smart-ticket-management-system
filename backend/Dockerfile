# Build the application
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Handle Windows line endings and grant Linux executable permissions
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src/ src/

# Tests are skipped during packaging so the image build does not depend on a running db.
# Run tests separately before building the image.
RUN ./mvnw -B -DskipTests package

# Run the application
FROM eclipse-temurin:21-jre
WORKDIR /backend

COPY --from=build --chown=10001:10001 /workspace/target/backend.jar backend.jar

# Switch to non-root
USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "backend.jar"]