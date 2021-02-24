FROM openjdk:8-jdk-alpine

WORKDIR /usr/src/publish-to-rabbit

USER root

RUN apk update -qq && apk add \
  maven

COPY . .

CMD mvn spring-boot:run