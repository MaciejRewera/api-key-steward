FROM sbtscala/scala-sbt:eclipse-temurin-alpine-22_36_1.10.1_3.5.0 as builder

WORKDIR /app
COPY . /app

RUN sbt assembly


FROM openjdk:11-jre-slim

COPY --from=builder /app/target/scala-2.13/api-key-steward.jar /api-key-steward.jar

CMD java -jar api-key-steward.jar
