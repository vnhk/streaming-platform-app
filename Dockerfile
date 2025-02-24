FROM maven:3.8.5-openjdk-17 as builder

WORKDIR /app

COPY --from=bervan-utils /app/core.jar /app/core.jar
COPY --from=bervan-utils /app/history-tables-core.jar /app/history-tables-core.jar
COPY --from=bervan-utils /app/ie-entities.jar /app/ie-entities.jar

RUN mvn install:install-file -Dfile=./core.jar -DgroupId=com.bervan -DartifactId=core -Dversion=latest -Dpackaging=jar -DgeneratePom=true
RUN mvn install:install-file -Dfile=./history-tables-core.jar -DgroupId=com.bervan -DartifactId=history-tables-core -Dversion=latest -Dpackaging=jar -DgeneratePom=true
RUN mvn install:install-file -Dfile=./ie-entities.jar -DgroupId=com.bervan -DartifactId=ie-entities -Dversion=latest -Dpackaging=jar -DgeneratePom=true

COPY --from=common-vaadin /app/common-vaadin.jar /app/common-vaadin.jar
RUN mvn install:install-file -Dfile=./common-vaadin.jar -DgroupId=com.bervan -DartifactId=common-vaadin -Dversion=latest -Dpackaging=jar -DgeneratePom=true

COPY --from=file-storage-app /app/file-storage-app.jar /app/file-storage-app.jar
RUN mvn install:install-file -Dfile=./file-storage-app.jar -DgroupId=com.bervan -DartifactId=file-storage-app -Dversion=latest -Dpackaging=jar -DgeneratePom=true

COPY /src ./src
COPY /pom.xml ./pom.xml
RUN mvn install -DskipTests -U

FROM maven:3.8.5-openjdk-17

COPY --from=builder /app/target/streaming-platform-app.jar /app/streaming-platform-app.jar