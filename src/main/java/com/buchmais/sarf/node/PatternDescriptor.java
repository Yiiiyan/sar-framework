package com.buchmais.sarf.node;

import com.buchmais.sarf.classification.criterion.data.node.RuleDescriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;

/**
 * @author Stephan Pirnbaum
 */
@Label("Pattern")
public interface PatternDescriptor extends RuleDescriptor {}
