package com.buschmais.sarf.plugin.api.criterion;

import com.buschmais.jqassistant.plugin.java.api.model.TypeDescriptor;
import com.buschmais.sarf.framework.metamodel.ComponentDescriptor;
import com.buschmais.sarf.framework.repository.AnnotationResolver;
import com.buschmais.sarf.framework.repository.ComponentRepository;
import com.buschmais.sarf.framework.repository.TypeRepository;
import com.buschmais.sarf.plugin.api.ClassificationInfoDescriptor;
import com.buschmais.sarf.plugin.api.ExecutedBy;
import com.buschmais.sarf.plugin.api.Executor;
import com.buschmais.sarf.plugin.packagenaming.PackageNamingRuleDescriptor;
import com.buschmais.sarf.plugin.packagenaming.PackageNamingRuleExecutor;
import com.buschmais.xo.api.Query.Result;
import com.buschmais.xo.api.XOManager;
import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Stephan Pirnbaum
 */
@Service
@Lazy
public class RuleBasedCriterionExecutor<C extends RuleBasedCriterionDescriptor> implements Executor<C, ComponentDescriptor> {

    private static final Logger LOG = LogManager.getLogger(RuleBasedCriterionExecutor.class);

    private XOManager xoManager;

    @Autowired
    BeanFactory beanFactory;

    @Autowired
    @Lazy
    public RuleBasedCriterionExecutor(XOManager xoManager) {
        this.xoManager = xoManager;
    }

    @Override
    public Set<ComponentDescriptor> execute(C executableDescriptor) {
        LOG.info("Executing " + this.getClass().getSimpleName());
        Set<ComponentDescriptor> componentDescriptors = new TreeSet<>((c1, c2) -> {
            int res = 0;
            if ((res = c1.getShape().compareTo(c2.getShape())) == 0) {
                res = c1.getName().compareTo(c2.getName());
            }
            return res;
        });
//        this.xoManager.currentTransaction().begin();
        Set<RuleDescriptor> rules = executableDescriptor.getRules();
        Map<String, Map<String, Set<String>>> mappedTypes = new HashMap<>();
        Long internalTypes = this.xoManager.getRepository(TypeRepository.class).countAllInternalTypes();
        for (RuleDescriptor r : rules) {
            ExecutedBy executedBy = AnnotationResolver.resolveAnnotation(r.getClass(), ExecutedBy.class);
            RuleExecutor<RuleDescriptor> ruleExecutor = (RuleExecutor<RuleDescriptor>) this.beanFactory.getBean(executedBy.value());
            ComponentDescriptor componentDescriptor = getOrCreateComponentOfCurrentIteration(r);
            Set<TypeDescriptor> ts = ruleExecutor.execute(r);
            for (TypeDescriptor t : ts) {
                ClassificationInfoDescriptor info = this.xoManager.create(ClassificationInfoDescriptor.class);
                info.setComponent(componentDescriptor);
                info.setType(t);
                info.setWeight(r.getWeight() / 100);
                info.setRule(r);
                info.setIteration(executableDescriptor.getIteration());
                executableDescriptor.getClassifications().add(info);
                if (mappedTypes.containsKey(t.getFullQualifiedName())) {
                    mappedTypes.get(t.getFullQualifiedName()).merge(
                        componentDescriptor.getShape(),
                        Sets.newHashSet(componentDescriptor.getName()),
                        (s1, s2) -> {
                            s1.addAll(s2);
                            return s1;
                        }
                    );
                } else {
                    Map<String, Set<String>> start = new HashMap<>();
                    start.put(componentDescriptor.getShape(), Sets.newHashSet(componentDescriptor.getName()));
                    mappedTypes.put(t.getFullQualifiedName(), start);
                }
            }
            componentDescriptors.add(componentDescriptor);
        }
        Long multipleMatched = 0L;
        for (Map.Entry<String, Map<String, Set<String>>> mappings : mappedTypes.entrySet()) {
            for (Map.Entry<String, Set<String>> components : mappings.getValue().entrySet()) {
                if (components.getValue().size() > 1) {
                    multipleMatched++;
                    break;
                }
            }
        }
 //       this.xoManager.currentTransaction().commit();
        LOG.info("Executed " + rules.size() + " Rules");
        LOG.info("\tIdentified " + componentDescriptors.size() + " Components");
        LOG.info("\tCoverage = " + (mappedTypes.size() / (double) internalTypes));
        LOG.info("\tQuality = " + (1 - multipleMatched / (double) mappedTypes.size()));
        return componentDescriptors;
    }

    private ComponentDescriptor getOrCreateComponentOfCurrentIteration(RuleDescriptor rule) {
        ComponentRepository repository = this.xoManager.getRepository(ComponentRepository.class);
        Result<ComponentDescriptor> result = repository.getComponentOfCurrentIteration(rule.getShape(), rule.getName());
        ComponentDescriptor componentDescriptor;
        if (result.hasResult()) {
            componentDescriptor = result.getSingleResult();
        } else {
            componentDescriptor = this.xoManager.create(ComponentDescriptor.class);
            componentDescriptor.setShape(rule.getShape());
            componentDescriptor.setName(rule.getName());
        }
        return componentDescriptor;
    }
}
