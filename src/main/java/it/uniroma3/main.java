package it.uniroma3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Scanner;

/**
 * Unico file con:
 *  - indicizzazione su DUE INDICI separati (nome e contenuto)
 *  - ricerca da console con sintassi: "nome ..." oppure "contenuto ..."
 *
 * Dipendenze (Maven):
 *   lucene-core, lucene-analyzers-common, lucene-queryparser (>= 9.x)
 *
 * Struttura progetto:
 *   data/               <-- 20 .txt (titolo = nome del file, testo = contenuto)
 *   index_nome/         <-- indice per i nomi (creato dal programma)
 *   index_contenuto/    <-- indice per i contenuti (creato dal programma)
 */
public class Main {

    // Cartelle predefinite (relative alla root del progetto)
    private static final Path DATA_DIR          = Paths.get("data");
    private static final Path INDEX_DIR_NOME    = Paths.get("index_nome");
    private static final Path INDEX_DIR_CONTEN  = Paths.get("index_contenuto");

    public static void main(String[] args) throws Exception {
        // 1) INDICIZZAZIONE SU DUE INDICI SEPARATI
        System.out.println("== Indicizzazione ==");
        var stats = Indexer.indexBoth(DATA_DIR, INDEX_DIR_NOME, INDEX_DIR_CONTEN);
        System.out.printf("Indicizzati %d file (nome) e %d file (contenuto) in %d ms%n",
                stats.filesNome, stats.filesContenuto, stats.millis);

        // 2) RICERCA INTERATTIVA
        try (Searcher searcher = new Searcher(INDEX_DIR_NOME, INDEX_DIR_CONTEN);
             Scanner sc = new Scanner(System.in)) { // <-- chiusura automatica (niente resource leak)

            System.out.println("""
                    
                    == Ricerca ==
                    Scrivi una query che inizi con:
                      - nome <termini...>            es: nome superclassico
                      - contenuto <termini...>       es: contenuto "amore e strada"
                    Digita 'exit' per uscire.
                    """);

            while (true) {
                System.out.print("> ");
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.equalsIgnoreCase("exit")) break;
                if (line.isBlank()) continue;

                try {
                    Searcher.Result result = searcher.search(line, 10);
                    TopDocs hits = result.hits;

                    System.out.printf("Risultati: %d (mostro i primi %d)%n",
                            hits.totalHits.value, Math.min(hits.scoreDocs.length, 10));

                    for (int i = 0; i < Math.min(10, hits.scoreDocs.length); i++) {
                        ScoreDoc sd = hits.scoreDocs[i];
                        // ⚠️ Nessun metodo deprecato: uso StoredFields API
                        Document d = result.doc(sd.doc);
                        System.out.printf("#%d  score=%.4f  file=%s  path=%s%n",
                                i + 1, sd.score, d.get("nomefile"), d.get("path"));
                    }
                } catch (Exception e) {
                    System.out.println("Errore query: " + e.getMessage());
                }
            }
        }

        System.out.println("Bye!");
    }

    /* =========================================================
       ===============  INDICIZZAZIONE (DUE INDICI)  ===========
       ========================================================= */

    private static final class Indexer {

        // Analyzer per i NOMI: tokenizza su whitespace e porta a minuscolo (niente stemming)
        private static Analyzer buildNomeAnalyzer() throws IOException {
            return CustomAnalyzer.builder()
                    .withTokenizer("whitespace")
                    .addTokenFilter("lowercase")
                    .build();
        }

        // Analyzer per i CONTENUTI: ItalianAnalyzer (stopword + stemming IT)
        private static Analyzer buildContenutoAnalyzer() {
            return new ItalianAnalyzer();
        }

        public static class Stats {
            public final int filesNome;
            public final int filesContenuto;
            public final long millis;
            public Stats(int fn, int fc, long ms) { this.filesNome = fn; this.filesContenuto = fc; this.millis = ms; }
        }

        /**
         * Indicizza TUTTI i .txt della cartella data/ in DUE INDICI SEPARATI:
         *  - index_nome/      campo "nomefile" (TextField, analizzato) + "path" (StoredField)
         *  - index_contenuto/ campo "contenuto" (TextField, analizzato) + "nomefile" e "path" (StoredField)
         */
        public static Stats indexBoth(Path dataDir, Path indexNomeDir, Path indexContenDir) throws Exception {
            if (!Files.isDirectory(dataDir)) {
                throw new IllegalArgumentException("DATA_DIR non esiste o non è una directory: " + dataDir.toAbsolutePath());
            }

            // Pulisci/crea directory indici
            Files.createDirectories(indexNomeDir);
            Files.createDirectories(indexContenDir);

            Analyzer nomeAnalyzer = buildNomeAnalyzer();
            Analyzer contenAnalyzer = buildContenutoAnalyzer();

            IndexWriterConfig cfgNome = new IndexWriterConfig(nomeAnalyzer);
            cfgNome.setOpenMode(OpenMode.CREATE); // ricrea ogni volta

            IndexWriterConfig cfgCont = new IndexWriterConfig(contenAnalyzer);
            cfgCont.setOpenMode(OpenMode.CREATE); // ricrea ogni volta

            int countNome = 0, countCont = 0;
            Instant t0 = Instant.now();

            try (Directory dirNome = FSDirectory.open(indexNomeDir);
                 Directory dirCont = FSDirectory.open(indexContenDir);
                 IndexWriter wNome = new IndexWriter(dirNome, cfgNome);
                 IndexWriter wCont = new IndexWriter(dirCont, cfgCont)) {

                try (var paths = Files.walk(dataDir)) {
                    for (Path p : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                        String fn = p.getFileName().toString().toLowerCase(Locale.ITALY);
                        if (!fn.endsWith(".txt")) continue;

                        String fileName = p.getFileName().toString();
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        String absPath = p.toAbsolutePath().toString();

                        // --- Documento per INDICE "NOME" ---
                        Document dn = new Document();
                        // Campo ricercabile sul nome (analizzato): supporta parole e "phrase query"
                        dn.add(new TextField("nomefile", fileName, Field.Store.YES));
                        // Campo path per stampa
                        dn.add(new StoredField("path", absPath));
                        wNome.addDocument(dn);
                        countNome++;

                        // --- Documento per INDICE "CONTENUTO" ---
                        Document dc = new Document();
                        // Campo ricercabile sul contenuto (analizzato, NO store per indice leggero)
                        dc.add(new TextField("contenuto", content, Field.Store.NO));
                        // Memorizzo anche nome e path per stampa dei risultati
                        dc.add(new StoredField("nomefile", fileName));
                        dc.add(new StoredField("path", absPath));
                        wCont.addDocument(dc);
                        countCont++;
                    }
                }

                wNome.commit();
                wCont.commit();
            }

            long ms = Duration.between(t0, Instant.now()).toMillis();
            return new Stats(countNome, countCont, ms);
        }
    }

    /* =========================================================
       ======================  RICERCA  ========================
       ========================================================= */

    private static final class Searcher implements AutoCloseable {

        private final DirectoryReader readerNome;
        private final DirectoryReader readerCont;
        private final IndexSearcher searcherNome;
        private final IndexSearcher searcherCont;
        private final Analyzer nomeAnalyzer;
        private final Analyzer contenAnalyzer;

        public Searcher(Path indexNome, Path indexContenuto) throws Exception {
            this.readerNome = DirectoryReader.open(FSDirectory.open(indexNome));
            this.readerCont = DirectoryReader.open(FSDirectory.open(indexContenuto));
            this.searcherNome = new IndexSearcher(readerNome);
            this.searcherCont = new IndexSearcher(readerCont);
            this.nomeAnalyzer = Indexer.buildNomeAnalyzer();
            this.contenAnalyzer = Indexer.buildContenutoAnalyzer();
        }

        /** wrapper del risultato per sapere quale indice ha risposto e recuperare i Document senza API deprecate */
        public static final class Result {
            public final TopDocs hits;
            private final IndexSearcher origin; // chi ha eseguito la query (nome o contenuto)

            private Result(TopDocs hits, IndexSearcher origin) {
                this.hits = hits;
                this.origin = origin;
            }

            public Document doc(int docId) throws IOException {
                // ✅ niente IndexSearcher.doc(int) deprecato
                return origin.storedFields().document(docId);
            }
        }

        /**
         * Sintassi:
         *   - "nome <termini...>"        (QueryParser sul campo "nomefile")
         *   - "contenuto <termini...>"   (QueryParser sul campo "contenuto")
         * Supporto "phrase query" con virgolette.
         */
        public Result search(String consoleQuery, int k) throws Exception {
            String q = consoleQuery.trim();
            int sp = q.indexOf(' ');
            if (sp < 0) throw new IllegalArgumentException("La query deve iniziare con 'nome' o 'contenuto' seguita dai termini.");
            String prefix = q.substring(0, sp).toLowerCase(Locale.ITALY);
            String terms = q.substring(sp + 1).trim();
            if (terms.isBlank()) throw new IllegalArgumentException("Specificare dei termini di ricerca.");

            switch (prefix) {
                case "nome" -> {
                    QueryParser qp = new QueryParser("nomefile", nomeAnalyzer);
                    Query query = qp.parse(terms);
                    TopDocs hits = searcherNome.search(query, k);
                    return new Result(hits, searcherNome);
                }
                case "contenuto" -> {
                    QueryParser qp = new QueryParser("contenuto", contenAnalyzer);
                    Query query = qp.parse(terms);
                    TopDocs hits = searcherCont.search(query, k);
                    return new Result(hits, searcherCont);
                }
                default -> throw new IllegalArgumentException("Prefisso non valido: usa 'nome' o 'contenuto'.");
            }
        }

        @Override
        public void close() throws Exception {
            readerNome.close();
            readerCont.close();
            // analyzer: nessuna risorsa nativa da chiudere
        }
    }
}
