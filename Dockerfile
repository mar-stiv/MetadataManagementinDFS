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

#5. set default environment variables
ENV MODE=server PORT=8081 SERVER_ID=1

#6. default command to start the program
# runs java Main first + lets docker expand the environment vars first
CMD ["sh","-c","java Main"]