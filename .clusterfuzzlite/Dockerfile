FROM maven:3.9-eclipse-temurin-21

WORKDIR /src/clearfolio
COPY . .

RUN mvn -q -DskipTests test-compile
CMD ["bash", "-lc", "echo Run Jazzer against src/test/java/com/clearfolio/viewer/controller/FuzzDownloadFilename.java"]
