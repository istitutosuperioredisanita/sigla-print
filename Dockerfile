# DOCKER-VERSION 1.12
FROM anapsix/alpine-java:jdk8
MAINTAINER Francesco Uliana <francesco.uliana@cnr.it>

COPY target/*.jar /opt/sigla-print.jar

EXPOSE 8080

# https://spring.io/guides/gs/spring-boot-docker/#_containerize_it
CMD ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/opt/sigla-print.jar"]