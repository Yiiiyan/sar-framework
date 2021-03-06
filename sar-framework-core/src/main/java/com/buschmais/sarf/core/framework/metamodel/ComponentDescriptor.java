package com.buschmais.sarf.core.framework.metamodel;

import com.buschmais.jqassistant.plugin.java.api.model.TypeDescriptor;
import com.buschmais.sarf.core.plugin.api.SARFDescriptor;
import com.buschmais.sarf.core.plugin.api.criterion.RuleDescriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;
import com.buschmais.xo.neo4j.api.annotation.Relation;
import com.buschmais.xo.neo4j.api.annotation.Relation.Incoming;
import com.buschmais.xo.neo4j.api.annotation.Relation.Outgoing;

import java.util.Set;

/**
 * Node representing a component of whatever shape is needed.
 *
 * @author Stephan Pirnbaum
 */
@Label("Component")
public interface ComponentDescriptor extends SARFDescriptor {

    @Outgoing
    Set<ComponentDependsOn> getComponentDependencies();

    @Incoming
    Set<ComponentDependsOn> getDependentComponents();

    @Relation("IDENTIFIED_BY")
    @Outgoing
    Set<RuleDescriptor> getIdentifyingRules();

    /**
     * Get the name of the component
     *
     * @return The name of the component
     */
    String getName();

    /**
     * Set the name of the component
     *
     * @param name The new name of the component
     */
    void setName(String name);

    /**
     * Get the shape of the component
     *
     * @return The shape of the component
     * @see ComponentDescriptor#setShape(String)
     */
    String getShape();

    /**
     * Set the shape of the component, a shape might for example be 'Module' or 'Layer'
     *
     * @param shape The new shape of the component
     */
    void setShape(String shape);

    @Relation("CONTAINS")
    @Outgoing
    Set<ComponentDescriptor> getContainedComponents();

    @Incoming
    Set<ComponentDescriptor> getContainingComponents();

    @Relation("CONTAINS")
    @Outgoing
    Set<TypeDescriptor> getContainedTypes();

    String[] getTopWords();

    void setTopWords(String[] topWords);
}
