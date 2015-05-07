package ue2.ir2015;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;

/**
 * Created by christianbors on 16/04/15.
 */
public class Search {
    public static void main(String[] args) throws IOException, ParseException {

        String usage =
                "Usage:\tjava lucene-search [-index dir] [-documents dir] [-updateIndex] [-topic file] [-similarity (bm25, bm25l)] [-output ranking] [-experiment name]";
        try {
            if (args.length == 0 || (args.length > 0 && ("-h".equals(args[0])) || "-help".equals(args[0]))) {
                System.out.println(usage);
                System.exit(0);
            }
        } catch (IndexOutOfBoundsException e) {
            System.out.println(usage);
            System.exit(0);
        }

        String indexPath = "index";
        String docsPath = "";
        boolean override = false;

        String topicFilename = "";
        String similarity = "default";
        String outputFile = "";
        String experimentName = "grp4";
        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i + 1];
                i++;
            } else if ("-topic".equals(args[i])) {
                topicFilename = args[i + 1];
                i++;
            } else if ("-similarity".equals(args[i])) {
                if (args[i+1].matches("bm25|bm25l")) {
                    similarity = args[i + 1];
                } else {
                    System.out.println(usage);
                    System.exit(0);
                }
                i++;
            } else if ("-output".equals(args[i])) {
                outputFile = args[i + 1];
            } else if ("-experiment".equals(args[i])) {
                experimentName += "_" + args[i + 1];
            } else if ("-updateIndex".equals(args[i])) {
                override = true;
            } else if ("-documents".equals(args[i])) {
                docsPath = args[i + 1];
            }
        }

        // create an index
        //      - if the index directory does not exist
        //      - if a documents directory is defined
        IndexCreator indexer = new IndexCreator();
        if (!indexer.indexExists(indexPath) || override) {
            if (docsPath.isEmpty()) {
                System.out.println("Documents directory not found: Please specify a documents path");
                System.out.println(usage);
                System.exit(0);
            }
            if (indexPath.equals("index")) {
                System.out.println("No costum index path specified, using './index/' ...");
            }
            indexer.indexFiles(docsPath, indexPath, override);
        }

        // perform search, with a
        SearchFiles searcher = new SearchFiles();
        TopDocs topDocs = searcher.search(indexPath, topicFilename, 100, similarity);
        // display results and write them to a file if a filename was specified
        searcher.writeResults(topicFilename, experimentName, topDocs, outputFile, 100, true);
        searcher.close();
    }
}
