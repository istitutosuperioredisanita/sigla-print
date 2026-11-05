# DOCKER-VERSION 17.10.0-ce
FROM eclipse-temurin:21-jre-alpine
MAINTAINER Francesco Uliana <francesco.uliana@cnr.it>

COPY target/*.war /opt/sigla-print.war

EXPOSE 8080

# https://spring.io/guides/gs/spring-boot-docker/#_containerize_it
CMD ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/opt/sigla-print.war"]
