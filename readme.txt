Come Avviarre il programma da compilatore

!! Verifica che tutto sia corretto !!
Controlla le versioni:
echo $JAVA_HOME
java -version
javac -version
mvn -v
Output atteso:
/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
openjdk version "17.0.16" ...
javac 17.0.16
Apache Maven 3.9.x

!! Rilancia il progetto !!
cd "/Users/marcodalbis/Documents/Homework 2 Ing dati/Homework-2-Ing-Dati"
mvn -q clean package
mvn -q exec:java -Dexec.mainClass="it.uniroma3.Main"

