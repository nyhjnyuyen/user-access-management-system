FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app
COPY target/user-service-*.jar user-service.jar

#khai bao cong nghe
EXPOSE 8082

#chay spring boot jar
ENTRYPOINT ["java", "-jar","user-service.jar"]
