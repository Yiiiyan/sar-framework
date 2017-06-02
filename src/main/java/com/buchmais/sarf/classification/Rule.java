package com.buchmais.sarf.classification;

import com.buchmais.sarf.SARFRunner;
import com.buchmais.sarf.node.ComponentDescriptor;
import com.buchmais.sarf.node.PatternDescriptor;
import com.buchmais.sarf.node.RuleDescriptor;
import com.buchmais.sarf.repository.ComponentRepository;
import com.buschmais.jqassistant.plugin.java.api.model.TypeDescriptor;
import com.buschmais.xo.api.Query.Result;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.XmlAttribute;
import java.util.Set;

/**
 * @author Stephan Pirnbaum
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE, force = true)
@EqualsAndHashCode(exclude = "descriptor")
public abstract class Rule<T extends RuleDescriptor> implements Comparable<Rule>, Materializable<RuleDescriptor> {

    @Getter
    @XmlAttribute(name = "shape")
    String shape;

    @Getter
    @XmlAttribute(name = "name")
    String name;

    @Getter
    @XmlAttribute(name = "weight")
    double weight;

    @Getter
    @XmlAttribute(name = "rule")
    String rule;

    T descriptor;

    public Rule(String shape, String name, double weight, String rule) {
        this.shape = shape;
        this.name = name;
        this.weight = weight;
        this.rule = rule;
    }

    public ComponentDescriptor getOrCreateComponentOfCurrentIteration() {
        ComponentRepository repository = SARFRunner.xoManager.getRepository(ComponentRepository.class);
        Result<ComponentDescriptor> result = repository.getComponentOfCurrentIteration(this.shape, this.name);
        ComponentDescriptor componentDescriptor;
        if (result.hasResult()) {
            componentDescriptor = result.getSingleResult();
        } else {
            componentDescriptor = SARFRunner.xoManager.create(ComponentDescriptor.class);
            componentDescriptor.setShape(this.shape);
            componentDescriptor.setName(this.name);
        }
        return componentDescriptor;
    }

    public T getDescriptor() {
        if (this.descriptor == null) {
            materialize();
        }
        return this.descriptor;
    }

    public abstract Set<TypeDescriptor> getMatchingTypes();

    public T materialize() {
        T descriptor = instantiateDescriptor();
        descriptor.setShape(this.shape);
        descriptor.setName(this.name);
        descriptor.setWeight(this.weight);
        descriptor.setRule(this.rule);
        this.descriptor = descriptor;
        return descriptor;
    }

    abstract T instantiateDescriptor();

    public int compareTo(Rule o) {
        if (!shape.equals(o.getShape())) return shape.compareTo(o.getShape());
        if (!name.equals(o.getName())) return name.compareTo(o.getName());
        if (!rule.equals(o.getRule())) return rule.compareTo(o.getRule());
        return (int) (weight - o.getWeight());
    }
}
