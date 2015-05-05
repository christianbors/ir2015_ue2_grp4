package test.ue2.ir2015;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import ue2.ir2015.Search;
import ue2.ir2015.SearchFiles;

/**
 * SearchFiles Tester.
 *
 * @author christianbors
 * @version 1.0
 * @since <pre>Apr 30, 2015</pre>
 */
public class SearchTest {

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
    }

    /**
     * Method: main(String[] args)
     */
    @Test
    public void testMain() throws Exception {
        for (int i = 1; i <= 20; ++i) {
            String[] param = {"-index", "newsgroups_index", "-documents", "20_newsgroups_subset", "-topic", "topics/topic" + i, "-output", "rankings_bm25.txt", "-experiment", "bm25", "-similarity", "bm25"};
            System.out.println("Search for Topic " + i);
            Search.main(param);
        }
    }

} 
