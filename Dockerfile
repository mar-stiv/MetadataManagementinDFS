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

#5. default command to start the program
# runs java Main first
CMD ["java", "Main"]