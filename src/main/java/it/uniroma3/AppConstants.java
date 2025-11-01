package src.main.java.it.uniroma3;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConstants {

    // 1. Percorso ASSOLUTO alla cartella 'data' con le 20 canzoni .txt
    public static final Path DATA_DIR = Paths.get("C:\\Users\\Marco\\Desktop\\Homework 2\\data"); 

    // 2. Percorso ASSOLUTO dove verrà creato l'indice Lucene (crea tu questa cartella)
    
    public static final Path INDEX_DIR = Paths.get("C:\\Users\\Marco\\Desktop\\Homework 2\\index");
}