package it.uniroma3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Importiamo le costanti di percorso che abbiamo definito
import static it.uniroma3.AppConstants.DATA_DIR;
import static it.uniroma3.AppConstants.INDEX_DIR;

public class Indexer {

    public static PerFieldAnalyzerWrapper createAnalyzerWrapper() {
        // Analizzatore 1: Per il campo nomefile (ricerca esatta)
        Analyzer nomeFileAnalyzer = new KeywordAnalyzer(); 
        // Analyzer 2: Per il campo contenuto (ricerca full-text in italiano con stemming e stopwords)
        Analyzer contenutoAnalyzer = new ItalianAnalyzer(); 

        // Mappa che associa gli analyzer ai campi
        Map<String, Analyzer> analyzerMap = new HashMap<>();
        analyzerMap.put("nomefile", nomeFileAnalyzer); 
        analyzerMap.put("contenuto", contenutoAnalyzer); 

        // PerFieldAnalyzerWrapper usa l'ItalianAnalyzer come default e sovrascrive
        // per i campi specificati nella mappa.
        return new PerFieldAnalyzerWrapper(new ItalianAnalyzer(), analyzerMap);
    }

    public void indexData() throws IOException {

        // 1. Definisci il tempo di inizio e il contatore
        long startTime = System.currentTimeMillis();
        AtomicInteger fileCount = new AtomicInteger(0);

        // 2. Definizione della configurazione dell'indice
        PerFieldAnalyzerWrapper analyzerWrapper = createAnalyzerWrapper(); // Usa l'Analyzer Wrapper
        IndexWriterConfig iwc = new IndexWriterConfig(analyzerWrapper);
        iwc.setOpenMode(OpenMode.CREATE); // Crea un nuovo indice, distruggendo il precedente se esiste

        // 3. Apertura del Writer
        try (Directory directory = FSDirectory.open(INDEX_DIR);
             IndexWriter writer = new IndexWriter(directory, iwc)) {

            // 4. Iterazione sui file
            Files.walk(DATA_DIR)
                 .filter(Files::isRegularFile)
                 .filter(p -> p.toString().toLowerCase().endsWith(".txt")) // Filtra solo i file .txt
                 .forEach(file -> {
                     try {
                         Document doc = new Document();
                         String fileName = file.getFileName().toString();
                         String fileContent = Files.readString(file);

                         // Campo nomefile: StringField (indice il token esatto), Store.YES per recuperare il nome
                         doc.add(new StringField("nomefile", fileName, Field.Store.YES));

                         // Campo contenuto: TextField (analizzato), Store.YES per recuperare il testo
                         doc.add(new TextField("contenuto", fileContent, Field.Store.YES));

                         writer.addDocument(doc);
                         fileCount.incrementAndGet();

                     } catch (IOException e) {
                         System.err.println("Errore nell'indicizzazione del file: " + file.getFileName() + ". Errore: " + e.getMessage());
                     }
                 });

            // 5. Commit e chiusura
            writer.commit();

            long endTime = System.currentTimeMillis();
            System.out.println("--- INDICIZZAZIONE COMPLETATA ---");
            System.out.println("Numero di file indicizzati: " + fileCount.get()); // [cite: 12]
            System.out.println("Tempo di indicizzazione: " + (endTime - startTime) + " ms"); // [cite: 12]

        } catch (Exception e) {
            System.err.println("Errore generale durante l'indicizzazione: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        new Indexer().indexData();
    }
}