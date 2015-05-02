package test.ue2.ir2015;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import ue2.ir2015.SearchFiles;

/**
 * SearchFiles Tester.
 *
 * @author <Authors name>
 * @version 1.0
 * @since <pre>Apr 30, 2015</pre>
 */
public class SearchFilesTest {

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
//        int i = 20;
            String[] param = {"-index", "newsgroups_index", "-queries", "topics/topic" + i};
            System.out.println("Search for Topic " + i);
            SearchFiles.main(param);
        }
    }

    /**
     * Method: doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query, int hitsPerPage, boolean raw, boolean interactive)
     */
    @Test
    public void testDoPagingSearch() throws Exception {
//TODO: Test goes here... 
    }


} 
