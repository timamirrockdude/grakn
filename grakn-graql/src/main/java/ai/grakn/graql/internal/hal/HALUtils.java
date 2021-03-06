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

package ai.grakn.graql.internal.hal;

import ai.grakn.concept.Concept;
import ai.grakn.concept.Label;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.concept.Thing;
import ai.grakn.graql.MatchQuery;
import ai.grakn.graql.Var;
import ai.grakn.graql.admin.Answer;
import ai.grakn.graql.admin.AnswerExplanation;
import ai.grakn.graql.admin.VarPatternAdmin;
import ai.grakn.graql.internal.pattern.property.IsaProperty;
import ai.grakn.graql.internal.pattern.property.RelationProperty;
import ai.grakn.graql.internal.reasoner.atom.Atom;
import ai.grakn.graql.internal.reasoner.atom.binary.RelationAtom;
import ai.grakn.graql.internal.reasoner.query.ReasonerAtomicQuery;
import ai.grakn.util.CommonUtil;
import ai.grakn.util.Schema;
import com.google.common.collect.Sets;
import com.theoryinpractise.halbuilder.api.Representation;
import ai.grakn.graql.internal.reasoner.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utils class used by HALBuilders
 *
 * @author Marco Scoppetta
 */
public class HALUtils {

    final static String EXPLORE_CONCEPT_LINK = "explore";

    // - Edges names

    final static String ISA_EDGE = Schema.EdgeLabel.ISA.getLabel();
    final static String SUB_EDGE = Schema.EdgeLabel.SUB.getLabel();
    final static String OUTBOUND_EDGE = "OUT";
    final static String INBOUND_EDGE = "IN";
    final static String RELATES_EDGE = Schema.EdgeLabel.RELATES.getLabel();
    final static String HAS_EDGE = "has";
    final static String PLAYS_EDGE = Schema.EdgeLabel.PLAYS.getLabel();
    final static String HAS_EMPTY_ROLE_EDGE = "EMPTY-GRAKN-ROLE";


    // - State properties

    public final static String ID_PROPERTY = "_id";
    public final static String TYPE_PROPERTY = "_type";
    public final static String BASETYPE_PROPERTY = "_baseType";
    public final static String DIRECTION_PROPERTY = "_direction";
    public final static String VALUE_PROPERTY = "_value";
    public final static String NAME_PROPERTY = "_name";
    public final static String LINKS_PROPERTY = "_links";

    public final static String INFERRED_RELATION = "inferred-relation";
    public final static String GENERATED_RELATION = "generated-relation";
    public final static String IMPLICIT_PROPERTY = "_implicit";


    static Schema.BaseType getBaseType(Thing thing) {
        if (thing.isEntity()) {
            return Schema.BaseType.ENTITY;
        } else if (thing.isRelationship()) {
            return Schema.BaseType.RELATIONSHIP;
        } else if (thing.isAttribute()) {
            return Schema.BaseType.ATTRIBUTE;
        } else if (thing.isRule()) {
            return Schema.BaseType.RULE;
        } else {
            throw CommonUtil.unreachableStatement("Unrecognised base type of " + thing);
        }
    }

    static Schema.BaseType getBaseType(SchemaConcept schemaConcept) {
        if (schemaConcept.isEntityType()) {
            return Schema.BaseType.ENTITY_TYPE;
        } else if (schemaConcept.isRelationshipType()) {
            return Schema.BaseType.RELATIONSHIP_TYPE;
        } else if (schemaConcept.isAttributeType()) {
            return Schema.BaseType.ATTRIBUTE_TYPE;
        } else if (schemaConcept.isRuleType()) {
            return Schema.BaseType.RULE_TYPE;
        } else if (schemaConcept.isRole()) {
            return Schema.BaseType.ROLE;
        } else if (schemaConcept.getLabel().equals(Schema.MetaSchema.THING.getLabel())) {
            return Schema.BaseType.TYPE;
        } else {
            throw CommonUtil.unreachableStatement("Unrecognised base type of " + schemaConcept);
        }
    }

    static void generateConceptState(Representation resource, Concept concept) {

        resource.withProperty(ID_PROPERTY, concept.getId().getValue());

        if (concept.isThing()) {
            Thing thing = concept.asThing();
            resource.withProperty(TYPE_PROPERTY, thing.type().getLabel().getValue())
                    .withProperty(BASETYPE_PROPERTY, getBaseType(thing).name());
        } else {
            resource.withProperty(BASETYPE_PROPERTY, getBaseType(concept.asSchemaConcept()).name());
        }

        if (concept.isAttribute()) {
            resource.withProperty(VALUE_PROPERTY, concept.asAttribute().getValue());
        }
        if (concept.isType()) {
            resource.withProperty(NAME_PROPERTY, concept.asType().getLabel().getValue());
            resource.withProperty(IMPLICIT_PROPERTY, ((SchemaConcept)concept).isImplicit());
        }
    }

    static Map<VarPatternAdmin, Pair<Map<Var, String>, String>> computeRoleTypesFromQuery(MatchQuery matchQuery, Answer firstAnswer) {
        final Map<VarPatternAdmin, Pair<Map<Var, String>, String>> roleTypes = new HashMap<>();
        AnswerExplanation firstExplanation = firstAnswer.getExplanation();
        if (firstExplanation.isEmpty()) {
            return computeRoleTypesFromQueryNoReasoner(matchQuery);
        } else {
            if (firstExplanation.isRuleExplanation() || firstExplanation.isLookupExplanation()) {
                updateRoleTypesFromAnswer(roleTypes, firstAnswer, matchQuery);
            } else {
                firstAnswer.getExplanation().getAnswers().forEach(answer -> updateRoleTypesFromAnswer(roleTypes, answer, matchQuery));
            }
            return roleTypes;
        }
    }

    private static void updateRoleTypesFromAnswer(Map<VarPatternAdmin, Pair<Map<Var, String>, String>> roleTypes, Answer answer, MatchQuery matchQuery) {
        Atom atom = ((ReasonerAtomicQuery) answer.getExplanation().getQuery()).getAtom();
        if (atom.isRelation()) {
            Optional<VarPatternAdmin> var = atom.getPattern().varPatterns().stream().filter(x -> x.hasProperty(RelationProperty.class)).findFirst();
            VarPatternAdmin varAdmin = atom.getPattern().asVarPattern();
            if (var.isPresent() && !var.get().var().isUserDefinedName() && bothRolePlayersAreSelected(atom, matchQuery)) {
                roleTypes.put(varAdmin, pairVarNamesRelationType(atom));
            }
        }
    }

    private static boolean bothRolePlayersAreSelected(Atom atom, MatchQuery matchQuery) {
        RelationAtom reasonerRel = ((RelationAtom) atom);
        Set<Var> rolePlayersInAtom = reasonerRel.getRolePlayers().stream().collect(Collectors.toSet());
        Set<Var> selectedVars = matchQuery.admin().getSelectedNames();
        //If all the role players contained in the current relation are also selected in the user query
        return Sets.intersection(rolePlayersInAtom, selectedVars).equals(rolePlayersInAtom);
    }

    private static boolean bothRolePlayersAreSelectedNoReasoner(VarPatternAdmin var, MatchQuery matchQuery) {
        Set<Var> rolePlayersInVar =  var.getProperty(RelationProperty.class).get().relationPlayers().stream().map(x->x.getRolePlayer().var()).collect(Collectors.toSet());
        Set<Var> selectedVars = matchQuery.admin().getSelectedNames();
        //If all the role players contained in the current relation are also selected in the user query
        return Sets.intersection(rolePlayersInVar, selectedVars).equals(rolePlayersInVar);
    }

    private static Map<VarPatternAdmin, Pair<Map<Var, String>, String>> computeRoleTypesFromQueryNoReasoner(MatchQuery matchQuery) {
        final Map<VarPatternAdmin, Pair<Map<Var, String>, String>> roleTypes = new HashMap<>();
        matchQuery.admin().getPattern().varPatterns().forEach(var -> {
            if (var.getProperty(RelationProperty.class).isPresent() && !var.var().isUserDefinedName() && bothRolePlayersAreSelectedNoReasoner(var,matchQuery)) {
                Map<Var, String> tempMap = new HashMap<>();
                var.getProperty(RelationProperty.class).get()
                        .relationPlayers().forEach(x -> {
                            tempMap.put(x.getRolePlayer().var(),
                                    (x.getRole().isPresent()) ? x.getRole().get().getPrintableName() : HAS_EMPTY_ROLE_EDGE);
                        }
                );
                String relationType = null;
                if (var.getProperty(IsaProperty.class).isPresent()) {
                    Optional<Label> relOptional = var.getProperty(IsaProperty.class).get().type().getTypeLabel();
                    relationType = (relOptional.isPresent()) ? relOptional.get().getValue() : "";
                } else {
                    relationType = "";
                }

                roleTypes.put(var, new Pair<>(tempMap, relationType));
            }
        });
        return roleTypes;
    }

    private static Pair<Map<Var, String>, String> pairVarNamesRelationType(Atom atom) {
        RelationAtom reasonerRel = ((RelationAtom) atom);
        Map<Var, String> varNamesToRole = new HashMap<>();
        // Put all the varNames in the map with EMPTY-ROLE role
        reasonerRel.getRolePlayers().forEach(varName -> varNamesToRole.put(varName, HAS_EMPTY_ROLE_EDGE));
        // Overrides the varNames that have roles in the previous map
        reasonerRel.getRoleVarMap().entries().stream().filter(entry -> !Schema.MetaSchema.isMetaLabel(entry.getKey().getLabel())).forEach(entry -> varNamesToRole.put(entry.getValue(), entry.getKey().getLabel().getValue()));

        String relationType = (reasonerRel.getSchemaConcept() != null) ? reasonerRel.getSchemaConcept().getLabel().getValue() : "";
        return new Pair<>(varNamesToRole, relationType);
    }

    static Map<VarPatternAdmin, Boolean> buildInferredRelationsMap(Answer firstAnswer) {
        final Map<VarPatternAdmin, Boolean> inferredRelations = new HashMap<>();
        AnswerExplanation firstExplanation = firstAnswer.getExplanation();
        if (firstExplanation.isRuleExplanation() || firstExplanation.isLookupExplanation()) {
            Atom atom = ((ReasonerAtomicQuery) firstAnswer.getExplanation().getQuery()).getAtom();
            if (atom.isRelation()) {
                VarPatternAdmin varAdmin = atom.getPattern().asVarPattern();
                inferredRelations.put(varAdmin, firstAnswer.getExplanation().isRuleExplanation());
            }
        } else {
            firstAnswer.getExplanation().getAnswers().forEach(answer -> {
                Atom atom = ((ReasonerAtomicQuery) answer.getExplanation().getQuery()).getAtom();
                if (atom.isRelation()) {
                    VarPatternAdmin varAdmin = atom.getPattern().asVarPattern();
                    inferredRelations.put(varAdmin, answer.getExplanation().isRuleExplanation());
                }
            });
        }

        return inferredRelations;
    }

}
