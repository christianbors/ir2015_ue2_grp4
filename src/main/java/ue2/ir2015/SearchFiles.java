package ue2.ir2015;

        /*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

/**
 * Simple command-line based search demo.
 */
public class SearchFiles {

    private SearchFiles() {
    }

    /**
     * Simple command-line based search demo.
     */
    public static void main(String[] args) throws Exception {
        String usage =
                "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-topic file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = "index";
        String field = "contents";
        String topicFolder = "topics/";
        String topicFilename = null;
        int repeat = 0;
        boolean raw = false;
//        String queryString = null;
        int hitsPerPage = 100;

        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                index = args[i + 1];
                i++;
            } else if ("-field".equals(args[i])) {
                field = args[i + 1];
                i++;
            } else if ("-topic".equals(args[i])) {
                topicFilename = args[i + 1];
                i++;
//            } else if ("-query".equals(args[i])) {
//                queryString = args[i + 1];
//                i++;
            } else if ("-repeat".equals(args[i])) {
                repeat = Integer.parseInt(args[i + 1]);
                i++;
            } else if ("-raw".equals(args[i])) {
                raw = true;
            } else if ("-paging".equals(args[i])) {
                hitsPerPage = Integer.parseInt(args[i + 1]);
                if (hitsPerPage <= 0) {
                    System.err.println("There must be at least 1 hit per page.");
                    System.exit(1);
                }
                i++;
            }
        }
        String topic = topicFolder + topicFilename;

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer();
        searcher.setSimilarity(new BM25Similarity());

        BufferedReader in = null;
        if (topicFilename != null) {
            in = Files.newBufferedReader(Paths.get(topic), StandardCharsets.UTF_8);
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }

        String line;
        String queryString = "";
        List<String> fields = new LinkedList<>();
        fields.add("contents");

        while ((line = in.readLine()) != null) {

//            String line = queryString != null ? queryString : in.readLine();
            line = line.trim();
            if (line.length() > 0) {

                field = "contents";
                if (line.contains(":")) {
                    if (!line.substring(0, line.indexOf(":")).contains(" ")) {
                        // determine field if it is in any way specific and add it to the list of fields
                        // to further use it in the MultiFieldQueryParser
                        field = line.substring(0, line.indexOf(":")).toLowerCase();
                        if (!field.toLowerCase().equals("lines") && !field.toLowerCase().equals("date")) {
                            fields.add(field);
                        }

                        line = line.substring(line.indexOf(":") + 2, line.length());
                    }
                    if (field.equals("path")) {
                        line = line.replace("!", " OR ");
                    }

                }

                if (!field.toLowerCase().equals("lines") && !field.toLowerCase().equals("date") && line.matches(".*[a-zA-Z]+.*")) {
                    if (!queryString.isEmpty()) {
                        queryString += " OR ";
                    }
                    // get rid of the dangerous escape characters in the content text
                    queryString += field + ":(" + QueryParserUtil.escape(line) + ")";
                }
            }
        }

        QueryParser parser = new QueryParser(field, analyzer);
        Query query = parser.parse(queryString);

        if (repeat > 0) {                           // repeat & time as benchmark
            Date start = new Date();
            for (int i = 0; i < repeat; i++) {
                searcher.search(query, 100);
            }
            Date end = new Date();
            System.out.println("Time: " + (end.getTime() - start.getTime()) + "ms");
        }

        doPagingSearch(in, searcher, query, topicFilename, hitsPerPage);

        reader.close();
    }

    /**
     * This demonstrates a typical paging search scenario, where the search engine presents
     * pages of size n to the user. The user can then go to the next page if interested in
     * the next hits.
     * <p>
     * When the query is executed for the first time, then only enough results are collected
     * to fill 5 result pages. If the user wants to page beyond this limit, then the query
     * is executed another time and all hits are collected.
     */
    public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query,
                                      String topicFilename, int hitsPerPage) throws IOException {

        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

        int start = 0;
        int end = Math.min(numTotalHits, hitsPerPage);

        FileOutputStream out = new FileOutputStream("the-file-name");

//        while (true) {
//            if (end > hits.length) {
//                System.out.println("Only results 1 - " + hits.length + " of " + numTotalHits + " total matching documents collected.");
//                System.out.println("Collect more (y/n) ?");
//                String line = in.readLine();
//                if (line.length() == 0 || line.charAt(0) == 'n') {
//                    break;
//                }
//
//                hits = searcher.search(query, numTotalHits).scoreDocs;
//            }

        end = Math.min(hits.length, start + hitsPerPage);
        try (Writer writer = new BufferedWriter(new FileWriter("ranking.txt", true))) {
                //new OutputStreamWriter(new FileOutputStream("ranking.txt"), "utf-8"))) {

            for (int i = start; i < end; i++) {

                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                if (path != null) {
                    String rank = topicFilename + " " +
                            "Q0 " +
                            path + " " +
                            (i + 1) + " " +
                            hits[i].score + " " +
                            "bm25\n";
//                    System.out.println(rank);
                    writer.write(rank);
//                    System.out.println((i + 1) + ". " + path);
//                    String title = doc.get("title");
//                    if (title != null) {
//                        System.out.println("   Title: " + doc.get("title"));
//                    }
                } else {
                    System.out.println((i + 1) + ". " + "No path for this document");
                }

            }
        }
//        }
    }
}