package ue2.ir2015;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SearchFiles {

    private IndexReader indexReader;
    private IndexSearcher indexSearcher;

    public TopDocs search(String index, String topicFile, int hits, String similarity) {

        try {
            this.indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
            indexSearcher = new IndexSearcher(indexReader);
        } catch (IOException e) {
            System.out.println("Index Directory not found: " + index);
            e.printStackTrace();
        }

        Analyzer analyzer = new StandardAnalyzer();
        if (similarity.equals("bm25")) {
            indexSearcher.setSimilarity(new BM25Similarity());
        } else if (similarity.equals("bm25l")) {
            indexSearcher.setSimilarity(new BM25LSimilarity());
        }

        BufferedReader in = null;
        if (topicFile != null) {
            try {
                in = Files.newBufferedReader(Paths.get(topicFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Topic File not found: " + Paths.get(topicFile));
                e.printStackTrace();
            }
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }

        String line;
        String field;
        BooleanQuery booleanQuery = new BooleanQuery();

        try {
            while ((line = in.readLine()) != null) {

                line = line.trim();
                if (line.length() > 0) {

                    field = "contents";
                    if (line.contains(":")) {
                        if (!line.substring(0, line.indexOf(":")).contains(" ")) {
                            // determine field if it is in any way specific and add it to the list of fields
                            // to further use it in the MultiFieldQueryParser
                            field = line.substring(0, line.indexOf(":")).toLowerCase();
                            if (field.equals("article-i.d.")) {
                                field = field.replace(".", "");
                            }
                        }

                        if (!field.equals("contents")) {
                            line = line.substring(line.indexOf(":") + 2, line.length());
                        }
                    }

                    if (field.equals("path")) {
                        String[] paths = line.split("!");
                        for (String path : paths) {
                            booleanQuery.add(new BooleanClause(new TermQuery(new Term(field, path)), BooleanClause.Occur.SHOULD));
                        }
                    } else if (field.equals("newsgroups") || field.equals("keywords")) {
                        String[] values = line.split(",");
                        for (String value : values) {
                            booleanQuery.add(new BooleanClause(new TermQuery(new Term(field, value)), BooleanClause.Occur.SHOULD));
                        }
                    } else if (field.equals("xref")) {
                        String[] xrefs = line.split(" ");
                        for (String xref : xrefs) {
                            booleanQuery.add(new BooleanClause(new TermQuery(new Term(field, xref)), BooleanClause.Occur.SHOULD));
                        }
                    } else if (field.equals("references")) {
                        String[] references = line.split(" |,");
                        for (String ref : references) {
                            booleanQuery.add(new BooleanClause(new TermQuery(new Term(field, ref)), BooleanClause.Occur.SHOULD));
                        }
                    } else if (field.matches("from|message-id|organization|sender|followup-to|article-id|" +
                            "nntp-posting-host|reply-to|distribution|return-receipt-to|nf-from|nf-id|x-newsreader")) {
                        Query tq = new TermQuery(new Term(field, line));
                        booleanQuery.add(new BooleanClause(tq, BooleanClause.Occur.SHOULD));
                    } else if (!field.equals("lines") && line.matches(".*[a-zA-Z]+.*")) {
                        // ignore the field lines
                        // field matches subject, or entry text
                        // only add line content if it actually contains words
                        booleanQuery.add(new BooleanClause(new QueryParser(field, analyzer)
                                .parse(QueryParserUtil.escape(line)), BooleanClause.Occur.SHOULD));
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error while reading: " + Paths.get(topicFile));
//            e.printStackTrace();
        } catch (ParseException e) {
            System.out.println("Parsing failed");
//            e.printStackTrace();
        }
        TopDocs results = null;
        try {
            results = indexSearcher.search(booleanQuery, hits);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    public void writeResults(String topicFilename, String experimentName, TopDocs topDocs, String outputFilename, int maxDocuments, boolean append) {

        ScoreDoc[] hits = topDocs.scoreDocs;

        try {
            for (int i = 0; i < maxDocuments; i++) {
                Document doc = indexSearcher.doc(hits[i].doc);
                String path = doc.get("docPath").replace("20_newsgroups_subset/", "");
                String topic = topicFilename.replace("topics/", "");
                if (path != null) {
                    String rank = topic + " " +
                            "Q0 " +
                            path + " " +
                            (i + 1) + " " +
                            hits[i].score + " " +
                            experimentName + "\n";
                    System.out.print(rank);
                } else {
                    System.out.println((i + 1) + ". " + "No path for this document");
                }

            }
            if (!outputFilename.isEmpty()) {
                try (Writer writer = new BufferedWriter(new FileWriter(outputFilename, append))) {
                    for (int i = 0; i < maxDocuments; i++) {
                        Document doc = indexSearcher.doc(hits[i].doc);
                        String path = doc.get("docPath").replace("20_newsgroups_subset/", "");
                        String topic = topicFilename.replace("topics/", "");
                        if (path != null) {
                            String rank = topic + " " +
                                    "Q0 " +
                                    path + " " +
                                    (i + 1) + " " +
                                    hits[i].score + " " +
                                    experimentName + "\n";
                            writer.write(rank);
                        } else {
                            System.out.println((i + 1) + ". " + "No path for this document");
                        }

                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            indexReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}