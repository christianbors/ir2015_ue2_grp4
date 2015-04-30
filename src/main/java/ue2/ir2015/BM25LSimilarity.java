package ue2.ir2015;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;


/**
 * Created by christianbors on 29/04/15.
 */
// BM25LSimilarity Class
// All methods from BM25Similarity are here so as to preserve functionality
public class BM25LSimilarity extends Similarity {

    private final float d;
    private final float k1;
    private final float b;

    public BM25LSimilarity() {
        // default values
        this.k1 = 1.2f;
        this.b = 0.75f;
        this.d = 0.5f;
    }

    public BM25LSimilarity(float d) {
        this.k1 = 1.2f;
        this.b = 0.75f;
        this.d = d;
    }

    public BM25LSimilarity(float k1, float b, float d) {
        this.k1 = k1;
        this.b = b;
        this.d = d;
    }

    protected float idf(long docFreq, long numDocs) {
        return (float) Math.log(1 + (numDocs - docFreq + 0.5D) / (docFreq + 0.5D));
    }

    protected float sloppyFreq(int distance) {
        return 1.0f / (distance + 1);
    }

    protected float scorePayLoad(int doc, int start, int end, BytesRef payload) {
        return 1;
    }

    protected float avgFieldLength(CollectionStatistics collectionStats) {
        final long sumTotalTermFreq = collectionStats.sumTotalTermFreq();
        if (sumTotalTermFreq <= 0) {
            return 1f;
        } else {
            return (float) (sumTotalTermFreq / (double) collectionStats.maxDoc());
        }
    }

    protected byte encodeNormValue(float boost, int fieldLength) {
        return SmallFloat.floatToByte315(boost / (float) Math.sqrt(fieldLength));
    }

    protected float decodeNormValue(byte b) {
        return NORM_TABLE[b & 0xFF];
    }

    protected boolean discountOverlaps = true;

    public void setDiscountOverlaps(boolean v) {
        discountOverlaps = v;
    }

    public boolean getDiscountOverlaps() {
        return discountOverlaps;
    }

    private static final float[] NORM_TABLE = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            float f = SmallFloat.byte315ToFloat((byte) i);
            NORM_TABLE[i] = 1.0f / (f * f);
        }
    }

    @Override
    public final long computeNorm(FieldInvertState state) {
        final int numTerms = discountOverlaps ? state.getLength() - state.getNumOverlap() : state.getLength();
        return encodeNormValue(state.getBoost(), numTerms);
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
        final long df = termStats.docFreq();
        final long max = collectionStats.maxDoc();
        final float idf = idf(df, max);

        return new Explanation(idf, "idf(docFreq=" + df + ", maxDocs=" + max + ")");
    }

    public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[])
    {
        final long max = collectionStats.maxDoc();
        float idf = 0.0f;

        final Explanation exp = new Explanation();
        exp.setDescription(("idf(), sum of:"));
        for(final TermStatistics stat : termStats)
        {
            final long df = stat.docFreq();
            final float termIdf = idf(df,max);
            exp.addDetail(new Explanation(termIdf, "idc(docFreq=" + df + ", maxDoc=" + max + ")"));
            idf += termIdf;
        }
        exp.setValue(idf);
        return exp;
    }

    @Override
    // BM25L implementation (add d parameter to cache[i] ?
    public final SimWeight computeWeight(float queryBoost, CollectionStatistics collectionStats, TermStatistics... termStats)
    {
        Explanation idf = termStats.length == 1 ? idfExplain(collectionStats, termStats[0]) : idfExplain(collectionStats, termStats);

        float avgdl = avgFieldLength(collectionStats);

        float cache[] = new float[256];

        for(int i = 0; i < cache.length; i++)
        {
            cache[i] = k1 * (((1-b) + b * decodeNormValue((byte) i) / avgdl) + d);
        }

        return new BM25LStats(collectionStats.field(), idf, queryBoost, avgdl, cache);

    }

    @Override
    public final SimScorer simScorer(SimWeight stats, LeafReaderContext context) throws IOException
    {
        BM25LStats bm25lstats = (BM25LStats) stats;
        return new BM25LDocScorer(bm25lstats, context.reader().getNormValues((bm25lstats.field)));
    }


    private class BM25LDocScorer extends SimScorer
    {
        private final BM25LStats stats;
        private final float weightValue;
        private final NumericDocValues norms;
        private final float[] cache;

        BM25LDocScorer(BM25LStats stats, NumericDocValues norms) throws IOException
        {
            this.stats = stats;
            this.weightValue = stats.weight * (k1 + 1);
            this.cache = stats.cache;
            this.norms = norms;
        }

        @Override
        public float score(int doc, float freq)
        {
            float norm = norms == null ? k1 : cache[(byte) norms.get(doc) & 0xFF];
            return weightValue * freq / (freq + norm);
        }

        @Override
        public Explanation explain(int doc, Explanation freq)
        {
            return explainScore(doc, freq, stats, norms);
        }

        @Override
        public float computeSlopFactor(int distance)
        {
            return sloppyFreq(distance);
        }

        @Override
        public float computePayloadFactor(int doc, int start, int end, BytesRef payload)
        {

            return scorePayLoad(doc, start, end, payload);
        }
    }

    private static class BM25LStats extends SimWeight {
        private final Explanation idf;
        private final float avgdl;
        private final float queryBoost;
        private float topLevelBoost;
        private float weight;
        private final String field;
        private final float cache[];


        BM25LStats(String field, Explanation idf, float queryBoost, float avgdl, float cache[]) {
            this.field = field;
            this.idf = idf;
            this.queryBoost = queryBoost;
            this.avgdl = avgdl;
            this.cache = cache;
        }

        @Override
        public float getValueForNormalization() {
            final float queryWeight = idf.getValue() * queryBoost;
            return queryWeight * queryWeight;
        }

        @Override
        public void normalize(float queryNorm, float topLevelBoost) {
            this.topLevelBoost = topLevelBoost;
            this.weight = idf.getValue() * queryBoost * topLevelBoost;
        }
    }
        private Explanation explainScore(int doc, Explanation freq, BM25LStats stats, NumericDocValues norms)
        {
            Explanation result = new Explanation();
            result.setDescription("score(doc=" + doc + ", freq=" + freq + "), product of:");

            Explanation boostExpl = new Explanation(stats.queryBoost * stats.topLevelBoost, "boost");
            if(boostExpl.getValue() != 1.0f)
            {
                result.addDetail(boostExpl);
            }

            result.addDetail(stats.idf);

            Explanation tfNormExpl = new Explanation();
            tfNormExpl.setDescription("tfNorm, computed from:");
            tfNormExpl.addDetail(freq);
            tfNormExpl.addDetail(new Explanation(k1, "parameter k1"));


            if(norms == null)
            {
                tfNormExpl.addDetail(new Explanation(0, "parameter b (norms omitted for field)"));
                tfNormExpl.setValue((freq.getValue() * (k1 +1 )) / (freq.getValue() + k1));
            }
            else
            {
                float doclen = decodeNormValue((byte) norms.get(doc));
                tfNormExpl.addDetail(new Explanation(b, "parameter b"));
                tfNormExpl.addDetail(new Explanation(d, "parameter d"));
                tfNormExpl.addDetail(new Explanation(stats.avgdl, "avgFieldLength"));
                tfNormExpl.addDetail(new Explanation(doclen, "fieldLength"));
                tfNormExpl.setValue((freq.getValue() * (k1 + 1)) / (freq.getValue() + k1 * (1 - b + b * doclen/stats.avgdl)));
            }

            result.addDetail(tfNormExpl);
            result.setValue(boostExpl.getValue() * stats.idf.getValue() * tfNormExpl.getValue());
            return result;
        }

        @Override
        public String toString()
        {
            return "BM25L(k1=" + k1 + ",b=" + b + ",d=" + d + ")";
        }

        public float getK1()
        {
            return k1;
        }


        public float getB()
        {
            return b;
        }

        public float getD()
        {
            return d;
        }
    }



