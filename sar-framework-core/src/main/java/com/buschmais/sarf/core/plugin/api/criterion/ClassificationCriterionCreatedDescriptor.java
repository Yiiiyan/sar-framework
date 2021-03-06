package com.buschmais.sarf.core.plugin.api.criterion;

import com.buschmais.xo.neo4j.api.annotation.Relation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Stephan Pirnbaum
 */
@Relation("CREATED")
@Retention(RetentionPolicy.RUNTIME)
public @interface ClassificationCriterionCreatedDescriptor {
}
