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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

public class IndexCreator {

    /**
     * Index all text files under a directory.
     */
    public void indexFiles(String docsPath, String indexPath, boolean override) {

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }
        Date start = new Date();
        try {
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();

            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            if (override) {        // Create a new index in the directory, removing any        // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }
            IndexWriter writer = new IndexWriter(dir, iwc);

            if (Files.isDirectory(docDir)) {
                Files.walkFileTree(docDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            indexDoc(writer, file);
                        } catch (IOException ignore) {
                            // don't index files that can't be read.
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                indexDoc(writer, docDir);
            }

            writer.close();
            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");
        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        }
    }

    /**
     * Indexes a single document
     */
    private void indexDoc(IndexWriter writer, Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            // make a new, empty document
            Document doc = new Document();
            // Add the path of the file as a field named "path".  Use a
            // field that is indexed (i.e. searchable), but don't tokenize
            // the field into separate words and don't index term frequency
            // or positional information:
            Field pathField = new StringField("docPath", file.toString(), Field.Store.YES);
            doc.add(pathField);

            // Add the contents of the file to a field named "contents".  Specify a Reader,
            // so that the text of the file is tokenized and indexed, but not stored.
            // Note that FileReader expects the file to be in UTF-8 encoding.
            // If that's not the case searching for special characters will fail.

            // Parse the text into the fields that were specified by the newsgroup header
            // if we cannot parse any field name we assume that we have arrived in the actual message segment
            // most fields should not be tokenized but saved as whole strings, hence we mostly use StringField
            // an exception is the subject, which could consist of a reply to a relevant document, thus we want
            // this field to be tokenized as well
            // path, newsgroups, keywords, xref, and references consist of values separated by specific characters
            // which need particular consideration
            BufferedReader documentReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            String field;
            while ((line = documentReader.readLine()) != null) {

                field = "contents";
                if (line.contains(":")) {
                    if (line.substring(0, line.indexOf(":")).contains(" ")) {
                        line = line.replace(":", "-");
                    } else {
                        field = line.substring(0, line.indexOf(":")).toLowerCase();
                        if (line.length() > line.indexOf(":") + 2) {
                            line = QueryParserUtil.escape(line.substring(line.indexOf(":") + 2, line.length()));
                        } else {
                            continue;
                        }
                    }
                }
                if (field.equals("path")) {
                    String[] paths = line.split("!");
                    for (String path : paths) {
                        doc.add(new StringField(field, path, Field.Store.YES));
                    }
                } else if (field.equals("newsgroups") || field.equals("keywords")) {
                    String[] values = line.split(",");
                    for (String value : values) {
                        doc.add(new StringField(field, value, Field.Store.YES));
                    }
                } else if (field.equals("xref")) {
                    String[] xrefs = line.split(" ");
                    for (String xref : xrefs) {
                        doc.add(new StringField(field, xref, Field.Store.YES));
                    }
                } else if (field.equals("references")) {
                    String[] references = line.split(" |,");
                    for (String ref : references) {
                        doc.add(new StringField(field, ref, Field.Store.YES));
                    }
                } else if (field.matches("from|message-id|organization|sender|followup-to|article-id|" +
                        "nntp-posting-host|reply-to|distribution|return-receipt-to|nf-from|nf-id|x-newsreader")) {
                    doc.add(new StringField(field, line, Field.Store.YES));
                } else if (field.matches("contents|subject") && line.matches(".*[a-zA-Z]+.*")) {
                    doc.add(new TextField(field, line, Field.Store.YES));
                }
            }
            if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                /// / New index, so we just add the document (no old document can be there):
//                System.out.println("adding " + file);
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
//                System.out.println("updating " + file);
                writer.updateDocument(new Term("docPath", file.toString()), doc);
            }
        }
    }

    public boolean indexExists(String indexPath) {
        try {
            return DirectoryReader.indexExists(FSDirectory.open(Paths.get(indexPath)));
        } catch (IOException e) {
            return false;
        }
    }
}