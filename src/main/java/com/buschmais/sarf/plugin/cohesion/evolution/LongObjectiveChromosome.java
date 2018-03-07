package com.buschmais.sarf.plugin.cohesion.evolution;

import com.buschmais.sarf.benchmark.MoJoCalculator;
import com.buschmais.sarf.benchmark.ModularizationQualityCalculator;
import com.google.common.collect.Sets;
import io.jenetics.LongChromosome;
import io.jenetics.LongGene;
import io.jenetics.util.ISeq;
import io.jenetics.util.IntRange;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Stephan Pirnbaum
 */
public abstract class LongObjectiveChromosome extends LongChromosome {

    private boolean evaluated = false;

    private double cohesionObjective = 0d;

    private double couplingObjective = 0d;

    private double componentSizeObjective = 0d;

    private double componentRangeObjective = 0d;

    private double cohesiveComponentObjective = 0d;

    protected LongObjectiveChromosome(ISeq<LongGene> genes) {
        super(genes, IntRange.of(genes.length()));
    }

    public LongObjectiveChromosome(Long min, Long max, int length) {
        super(min, max, length);
    }

    public LongObjectiveChromosome(Long min, Long max) {
        super(min, max);
    }

    private void evaluate() {
        // mapping from component id to a set of type ids
        Map<Long, Set<Long>> identifiedComponents = new HashMap<>();
        for (int i = 0; i < this.length(); i++) {
            identifiedComponents.merge(
                this.getGene(i).getAllele(),
                Sets.newHashSet(Partitioner.ids[i]),
                (s1, s2) -> {
                    s1.addAll(s2);
                    return s1;
                });
        }
        int uncohesiveComponents = 0;
        int subComponents = 0;
        int totalSubComponents = 0;
        // compute fitness for intra-edge coupling (cohesiveness of components)
        for (Map.Entry<Long, Set<Long>> component1 : identifiedComponents.entrySet()) {
            this.cohesionObjective += computeCohesion(component1.getValue());
            if ((subComponents = Problem.getInstance().connectedComponents(component1.getValue()).keySet().size()) > 1) {
                uncohesiveComponents++;
                totalSubComponents += subComponents;
            }
            // compute fitness for inter-edge coupling (coupling of components)
            // is compared twice -> punishing inter-edges todo similarity?
            for (Map.Entry<Long, Set<Long>> component2 : identifiedComponents.entrySet()) {
                if (!Objects.equals(component1.getKey(), component2.getKey())) {
                    this.couplingObjective -= computeCoupling(component1.getValue(), component2.getValue());
                }
            }
        }
        this.couplingObjective = normalizeCoupling(this.couplingObjective, identifiedComponents.size());
        this.cohesionObjective /= identifiedComponents.size();
        // minimize the difference between min and max component size
        this.componentRangeObjective = ((double) (identifiedComponents.values().stream().mapToInt(Set::size).min().orElse(0) -
            identifiedComponents.values().stream().mapToInt(Set::size).max().orElse(0))) / (Partitioner.ids.length - 1);
        // punish one-type only components
        //punish un-cohesive components
        this.cohesiveComponentObjective = uncohesiveComponents == 0 ? 1 : (totalSubComponents > identifiedComponents.size() ? 0 : (1 - ((double) totalSubComponents) / identifiedComponents.size()));
        this.componentSizeObjective = -identifiedComponents.values().stream().mapToInt(Set::size).filter(i -> i == 1).count() / (double) identifiedComponents.size();
        if (MoJoCalculator.reference != null) {
            writeBenchmarkLine(identifiedComponents);
        }
        this.evaluated = true;

    }

    protected abstract double computeCohesion(Collection<Long> ids);

    protected abstract double computeCoupling(Collection<Long> ids1, Collection<Long> ids2);

    protected abstract double normalizeCoupling(Double coupling, int components);

    protected double getCohesionObjective() {
        if (!this.evaluated) evaluate();
        return this.cohesionObjective;
    }

    protected double getCouplingObjective() {
        if (!this.evaluated) evaluate();
        return this.couplingObjective;
    }

    protected double getComponentSizeObjective() {
        if (!this.evaluated) evaluate();
        return this.componentSizeObjective;
    }

    protected double getComponentRangeObjective() {
        if (!this.evaluated) evaluate();
        return this.componentRangeObjective;
    }

    protected double getCohesiveComponentObjective() {
        if (!this.evaluated) evaluate();
        return this.cohesiveComponentObjective;
    }

    private void writeBenchmarkLine(Map<Long, Set<Long>> identifiedComponents) {
        MoJoCalculator moJoCalculator1 = new MoJoCalculator(identifiedComponents, true);
        MoJoCalculator moJoCalculator2 = new MoJoCalculator(identifiedComponents, false);
        MoJoCalculator moJoFmCalculator = new MoJoCalculator(identifiedComponents, true);
        MoJoCalculator moJoPlusCalculator1 = new MoJoCalculator(identifiedComponents, true);
        MoJoCalculator moJoPlusCalculator2 = new MoJoCalculator(identifiedComponents, false);
        Long mojoCompRef = moJoCalculator1.mojo();
        Long mojoRefComp = moJoCalculator2.mojo();
        Long mojo = Math.min(mojoCompRef, mojoRefComp);
        Double mojoFm = moJoFmCalculator.mojofm();
        Long mojoPlusCompRef = moJoPlusCalculator1.mojoplus();
        Long mojoPlusRefComp = moJoPlusCalculator2.mojoplus();
        Long mojoPlus = Math.min(mojoPlusCompRef, mojoPlusRefComp);
        Double fitness = this.cohesionObjective + this.couplingObjective + this.componentRangeObjective
            + this.componentSizeObjective + this.cohesiveComponentObjective;
        Double mQ = ModularizationQualityCalculator.computeMQ(identifiedComponents);
        try (FileWriter fw = new FileWriter("benchmark.csv", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.print(Partitioner.lastGeneration + 1 + " , ");
            out.print(identifiedComponents.size() + ", ");
            out.print(this.cohesionObjective + ", ");
            out.print(this.couplingObjective + ", ");
            out.print(this.componentSizeObjective + ", ");
            out.print(this.componentRangeObjective + ", ");
            out.print(this.cohesiveComponentObjective + ", ");
            out.print(mojo + ", ");
            out.print(mojoFm + ", ");
            out.print(mojoPlus + ", ");
            out.print(mQ + ", ");
            out.println(fitness);
        } catch (IOException e) {
        }
    }
}
