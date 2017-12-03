FROM openjdk:8
 
RUN mkdir -p /data/config
RUN mkdir -p /data/photos
RUN chown nobody /data/config
RUN chown nobody /data/photos
RUN usermod -d /tmp nobody
WORKDIR /
EXPOSE 8080
ENV RAOA_CONFIGURATION_BASE=/data/config
ENV RAOA_STORAGE_BASE=/data/photos
VOLUME ["/data", "/import"]
CMD ["java","-jar","app.jar"]
COPY target/server-*.jar /app.jar
USER nobody
