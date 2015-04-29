package ue2.ir2015;

import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.similarities.BM25Similarity;

/**
 * Created by christianbors on 29/04/15.
 */
public class BM25LSimilarity extends BM25Similarity
{

    private float delta;

    public BM25LSimilarity()
    {
        this(0.5f);
    }

    public BM25LSimilarity(float delta)
    {
        super();
        this.delta = delta;
    }

    public BM25LSimilarity(float k1, float b, float delta)
    {
        super(k1, b);
        this.delta = delta;
    }



    private class BM25LStats {

    }
}
