FROM maven:3.6.0-jdk-11-slim as build-env
ADD . /build/tfbridge
WORKDIR /build/tfbridge
RUN mvn clean install
RUN mkdir -p /app
RUN mv server/target/app*.jar /app/app.jar

FROM gcr.io/distroless/java:11
ENV PORT 8080
EXPOSE 8080
COPY --from=build-env /app /app
WORKDIR /app
CMD ["app.jar"]