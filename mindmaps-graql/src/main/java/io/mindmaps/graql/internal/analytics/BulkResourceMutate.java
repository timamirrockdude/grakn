/*
 * MindmapsDB - A Distributed Semantic Database
 * Copyright (C) 2016  Mindmaps Research Ltd
 *
 * MindmapsDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MindmapsDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MindmapsDB. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package io.mindmaps.graql.internal.analytics;

import io.mindmaps.MindmapsGraph;
import io.mindmaps.concept.Instance;
import io.mindmaps.concept.Relation;
import io.mindmaps.concept.RelationType;
import io.mindmaps.concept.Resource;
import io.mindmaps.concept.ResourceType;
import io.mindmaps.concept.RoleType;
import io.mindmaps.Mindmaps;
import io.mindmaps.exception.MindmapsValidationException;
import io.mindmaps.graql.internal.util.GraqlType;
import io.mindmaps.util.ErrorMessage;
import io.mindmaps.util.Schema;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Methods to deal with persisting values to a Mindmaps graph during OLAP computations. Each spark executor is thread
 * bound and responsible for a subset of the vertices in the graph. Therefore, an instance of the graph can be held in
 * each executor and the mutations from multiple vertices committed as batches. The need to delete relations in a
 * separate iterations from when new relations are added can also be facilitated.
 *
 * Each vertex program should instatiate the <code>BulkResourceMutate</code> class at the start of an iteration and call
 * the <code>close</code> method at the end of each iteration. Additionally <code>cleanup</code> must be called in a
 * separate iteration from <code>putValue</code> to ensure that the graph remains sound.
 */

class BulkResourceMutate <T>{

    private int batchSize = 100;
    private int numberOfRetries = 10;
    private MindmapsGraph graph;
    private int currentNumberOfVertices = 0;
    private final String resourceTypeId = Analytics.degree;
    private final String keyspace;
    private Map<String,T> resourcesToPersist = new HashMap<>();

    private ResourceType<T> resourceType;
    private RoleType resourceOwner;
    private RoleType resourceValue;
    private RelationType relationType;

    // This has been added for debugging purposes - set to true for debugging
    private boolean verboseOutput = false;

    public BulkResourceMutate(String keyspace) {
        this.keyspace = keyspace;
    }

    public BulkResourceMutate(String keyspace, int batchSize) {
        this(keyspace);
        this.batchSize = batchSize;
    }

    void putValue(Vertex vertex, T value) {
        currentNumberOfVertices++;
        initialiseGraph();

        if (verboseOutput) {
            System.out.println("considering vertex: "+vertex);
            vertex.properties().forEachRemaining(System.out::println);
        }

        String id = vertex.value(Schema.ConceptProperty.ITEM_IDENTIFIER.name());
        resourcesToPersist.put(id,value);

        if (currentNumberOfVertices >= batchSize) flush();
    }

    /**
     * Force all pending operations in the batch to be committed.
     */
    void flush() {
        boolean hasFailed = true;
        int numberOfFailures = 0;

        while (hasFailed) {
            hasFailed = false;
            try {
                persistResources();
            } catch (Exception e) {
                hasFailed = true;
                numberOfFailures++;
                if (!(numberOfFailures<numberOfRetries))
                    throw new RuntimeException(ErrorMessage.BULK_PERSIST.getMessage(resourceTypeId,e.getMessage()),e);
            }
        }

        resourcesToPersist.clear();
        currentNumberOfVertices = 0;
    }

    private void persistResources() throws MindmapsValidationException {
        initialiseGraph();

        resourcesToPersist.forEach((id, value) -> {
            Instance instance =
                    graph.getInstance(id);

            List<Relation> relations = instance.relations(resourceOwner).stream()
                    .filter(relation -> relation.rolePlayers().size() == 2)
                    .filter(relation -> relation.rolePlayers().containsKey(resourceValue) &&
                            relation.rolePlayers().get(resourceValue).type().getId().equals(resourceTypeId))
                    .collect(Collectors.toList());

            if (verboseOutput) {
                System.out.println("assertions currently attached");
                relations.forEach(System.out::println);
            }

            if (relations.isEmpty()) {
                Resource<T> resource = graph.putResource(value, resourceType);

                graph.addRelation(relationType)
                        .putRolePlayer(resourceOwner, instance)
                        .putRolePlayer(resourceValue, resource);

                return;
            }

            relations = relations.stream()
                    .filter(relation ->
                            relation.rolePlayers().get(resourceValue).asResource().getValue() != value)
                    .collect(Collectors.toList());

            if (!relations.isEmpty()) {
                graph.getRelation(relations.get(0).getId()).delete();

                Resource<T> resource = graph.putResource(value, resourceType);

                graph.addRelation(relationType)
                        .putRolePlayer(resourceOwner, instance)
                        .putRolePlayer(resourceValue, resource);
            }
        });

        graph.commit();
    }

    private void refreshOntologyElements() {
        resourceType = graph.getResourceType(resourceTypeId);
        resourceOwner = graph.getRoleType(GraqlType.HAS_RESOURCE_OWNER.getId(resourceTypeId));
        resourceValue = graph.getRoleType(GraqlType.HAS_RESOURCE_VALUE.getId(resourceTypeId));
        relationType = graph.getRelationType(GraqlType.HAS_RESOURCE.getId(resourceTypeId));
    }

    private void initialiseGraph() {
        if (graph == null) {
            graph = Mindmaps.factory(Mindmaps.DEFAULT_URI, keyspace).getGraphBatchLoading();
            graph.rollback();
            refreshOntologyElements();
        }
    }
}