/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 *
 */

package ai.grakn.graql.internal.query.match;

import ai.grakn.GraknTx;
import ai.grakn.graql.internal.pattern.Patterns;
import com.google.common.collect.Sets;
import org.junit.Test;

import static ai.grakn.graql.Graql.var;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

public class MatchQueryKBTest {

    private final AbstractMatchQuery query =
            new MatchQueryBase(Patterns.conjunction(Sets.newHashSet(var("x").admin())));

    @Test
    public void matchQueriesContainingTheSameGraphAndMatchQueryBaseAreEqual() {
        GraknTx graph = mock(GraknTx.class);

        MatchQueryTx query1 = new MatchQueryTx(graph, query);
        MatchQueryTx query2 = new MatchQueryTx(graph, query);

        assertEquals(query1, query2);
        assertEquals(query1.hashCode(), query2.hashCode());
    }

    @Test
    public void matchQueriesContainingDifferentGraphsAreNotEqual() {
        GraknTx graph1 = mock(GraknTx.class);
        GraknTx graph2 = mock(GraknTx.class);

        MatchQueryTx query1 = new MatchQueryTx(graph1, query);
        MatchQueryTx query2 = new MatchQueryTx(graph2, query);

        assertNotEquals(query1, query2);
    }
}