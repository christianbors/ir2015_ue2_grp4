package ue2.ir2015;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by christianbors on 16/04/15.
 */
public class Search {
    public static void main(String[] args) throws IOException, ParseException {
        /*Analyzer analyzer = new StandardAnalyzer();

        Directory index = new RAMDirectory();
        List<File> queue = new ArrayList<File>();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter iwriter = new IndexWriter(index, config);
        addDoc(iwriter, "Lucene in Action", "193398817");
        addDoc(iwriter, "Lucene for Dummies", "55320055Z");
        addDoc(iwriter, "Managing Gigabytes", "55063554A");
        addDoc(iwriter, "The Art of Computer Science", "9900333X");
        iwriter.close();
        String querystr = args.length > 0 ? args[0] : "lucene";

        Query q = null;
        try {
            q = new QueryParser("title", analyzer).parse(querystr);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        // 3. search
        int hitsPerPage = 10;
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        // 4. display results
        System.out.println("Found " + hits.length + " hits.");
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            System.out.println((i + 1) + ". " + d.get("isbn") + "\t" + d.get("title"));
        }

        // reader can only be closed when there
        // is no need to access the documents any more.
        reader.close();*/
        String usage =
                "Usage:\tjava lucene-search [-index dir] [-topic file] [-similarity (bm25, bm25l)] [-output ranking] [-experiment name]";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = "";
        String topicFilename = "";
        String similarity = "default";
        String outputFile = "";
        String experimentName = "grp4";
        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                index = args[i + 1];
                i++;
            } else if ("-field".equals(args[i])) {
//                field = args[i + 1];
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
            }
        }

        // perform search, with a
        SearchFiles searcher = new SearchFiles();
        TopDocs topDocs = searcher.search(index, topicFilename, 100, similarity);
        // display results and write them to a file if a filename was specified
        searcher.writeResults(topicFilename, experimentName, topDocs, outputFile, 100);
        searcher.close();
    }
}
