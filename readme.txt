Come Avviarre il programma da compilatore

🧭 1️⃣ Controlla se sei in nuovo terminale
Le variabili impostate con export JAVA_HOME=... durano solo nella sessione corrente.
Se hai chiuso e riaperto il terminale, devi avere quella riga dentro al file di configurazione (~/.zshrc).
Verifica:
cat ~/.zshrc | grep JAVA_HOME
Devi vedere qualcosa come:
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH=$JAVA_HOME/bin:$PATH

🧰 2️⃣ Se manca, rimettila subito
Esegui questi comandi:
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.zshrc
source ~/.zshrc

✅ 3️⃣ Verifica che tutto sia corretto
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

🚀 4️⃣ Ora rilancia il progetto
cd "/Users/marcodalbis/Documents/Homework 2 Ing dati/Homework-2-Ing-Dati"
mvn -q clean package
mvn -q exec:java -Dexec.mainClass="it.uniroma3.Main"

