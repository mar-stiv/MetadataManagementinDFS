#1. base image
# eclipse-temurin = the official OpenJDK distribution
# alpine = very small linux image
FROM eclipse-temurin:17-jdk-alpine

#2. working directory within the container
WORKDIR /app

#3. copy source code into this container
COPY *.java ./

#4. compile the java files
RUN javac *.java

#5. to ensure correct encoding in UTF-8
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

#6. default command to start the program
# runs java Main first
CMD ["java", "-Dfile.encoding=UTF-8", "Main"]