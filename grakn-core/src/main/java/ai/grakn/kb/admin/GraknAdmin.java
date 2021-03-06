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
 */

package ai.grakn.kb.admin;

import ai.grakn.GraknTx;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Label;
import ai.grakn.concept.LabelId;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.concept.Role;
import ai.grakn.concept.RuleType;
import ai.grakn.exception.InvalidKBException;
import ai.grakn.util.Schema;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Admin interface for {@link GraknTx}.
 *
 * @author Filipe Teixeira
 */
public interface GraknAdmin {

    /**
     *
     * @param vertex A vertex which contains properties necessary to build a concept from.
     * @param <T> The type of the concept being built
     * @return A concept built using the provided vertex
     */
    @CheckReturnValue
    <T extends Concept> T buildConcept(Vertex vertex);

    /**
     *
     * @param edge An {@link Edge} which contains properties necessary to build a {@link Concept} from.
     * @param <T> The type of the {@link Concept} being built
     * @return A {@link Concept} built using the provided {@link Edge}
     */
    @CheckReturnValue
    <T extends Concept> T buildConcept(Edge edge);


    /**
     * Utility function to get a read-only Tinkerpop traversal.
     *
     * @return A read-only Tinkerpop traversal for manually traversing the graph
     */
    @CheckReturnValue
    GraphTraversalSource getTinkerTraversal();

    /**
     * A flag to check if batch loading is enabled and consistency checks are switched off
     *
     * @return true if batch loading is enabled
     */
    @CheckReturnValue
    boolean isBatchTx();

    //------------------------------------- Meta Types ----------------------------------
    /**
     * Get the root of all Types.
     *
     * @return The meta type -> type.
     */
    @CheckReturnValue
    SchemaConcept getMetaConcept();

    /**
     * Get the root of all {@link RelationshipType}.
     *
     * @return The meta relation type -> relation-type.
     */
    @CheckReturnValue
    RelationshipType getMetaRelationType();

    /**
     * Get the root of all the {@link Role}.
     *
     * @return The meta role type -> role-type.
     */
    @CheckReturnValue
    Role getMetaRole();

    /**
     * Get the root of all the {@link AttributeType}.
     *
     * @return The meta resource type -> resource-type.
     */
    @CheckReturnValue
    AttributeType getMetaResourceType();

    /**
     * Get the root of all the Entity Types.
     *
     * @return The meta entity type -> entity-type.
     */
    @CheckReturnValue
    EntityType getMetaEntityType();

    /**
     * Get the root of all Rule Types;
     *
     * @return The meta rule type -> rule-type.
     */
    @CheckReturnValue
    RuleType getMetaRuleType();

    /**
     * Get the root of all inference rules.
     *
     * @return The meta rule -> inference-rule.
     */
    @CheckReturnValue
    RuleType getMetaRuleInference();

    /**
     * Get the root of all constraint rules.
     *
     * @return The meta rule -> constraint-rule.
     */
    @CheckReturnValue
    RuleType getMetaRuleConstraint();

    //------------------------------------- Admin Specific Operations ----------------------------------

    /**
     * Converts a Type Label into a type Id for this specific graph. Mapping labels to ids will differ between graphs
     * so be sure to use the correct graph when performing the mapping.
     *
     * @param label The label to be converted to the id
     * @return The matching type id
     */
    @CheckReturnValue
    LabelId convertToId(Label label);

    /**
     * Commits to the graph without submitting any commit logs.
     * @return the commit log that would have been submitted if it is needed.
     * @throws InvalidKBException when the graph does not conform to the object concept
     */
    Optional<String> commitNoLogs() throws InvalidKBException;

    /**
     * Check if there are duplicate resources in the provided set of vertex IDs
     * @param index index of the resource to find duplicates of
     * @param resourceVertexIds vertex Ids containing potential duplicates
     * @return true if there are duplicate resources and PostProcessing can proceed
     */
    boolean duplicateResourcesExist(String index, Set<ConceptId> resourceVertexIds);

    /**
     * Merges the provided duplicate resources
     *
     * @param resourceVertexIds The resource vertex ids which need to be merged.
     * @return True if a commit is required.
     */
    boolean fixDuplicateResources(String index, Set<ConceptId> resourceVertexIds);

    /**
     * Updates the counts of all the types
     *
     * @param conceptCounts The concepts and the changes to put on their counts
     */
    void updateConceptCounts(Map<ConceptId, Long> conceptCounts);

    /**
     * Creates a new shard for the concept
     * @param conceptId the id of the concept to shard
     */
    void shard(ConceptId conceptId);

    /**
     *
     * @param key The concept property tp search by.
     * @param value The value of the concept
     * @return A concept with the matching key and value
     */
    @CheckReturnValue
    @Nullable
    <T extends Concept> T getConcept(Schema.VertexProperty key, Object value);

    /**
     * Closes the root session this graph stems from. This will automatically rollback any pending transactions.
     */
    void closeSession();

    /**
     * Immediately closes the session and deletes the graph.
     * Should be used with caution as this will invalidate any pending transactions
     */
    void delete();

    /**
     * Get the URL where the graph is located
     * @return the URL where the graph is located
     */
    String getEngineUrl();
}
