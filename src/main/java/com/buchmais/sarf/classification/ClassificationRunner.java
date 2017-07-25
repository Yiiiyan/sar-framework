package com.buchmais.sarf.classification;

import com.buchmais.sarf.SARFRunner;
import com.buchmais.sarf.benchmark.MoJoCalculator;
import com.buchmais.sarf.classification.configuration.*;
import com.buchmais.sarf.classification.criterion.cohesion.CohesionCriterion;
import com.buchmais.sarf.metamodel.ComponentDescriptor;
import com.buchmais.sarf.repository.TypeRepository;
import com.buschmais.jqassistant.plugin.java.api.model.TypeDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.*;
import java.net.URL;
import java.util.Set;

/**
 * @author Stephan Pirnbaum
 */
public class ClassificationRunner { // TODO: 18.07.2017 AbstractRunner + BenchmarkRunner

    private ActiveClassificationConfiguration activeClassificationConfiguration;

    private static final Logger LOG = LogManager.getLogger(ClassificationRunner.class);

    private static ClassificationRunner ourInstance = new ClassificationRunner();

    public static ClassificationRunner getInstance() {
        return ourInstance;
    }

    private ClassificationRunner() {
    }

    public Double run(URL configUrl, URL benchmarkUrl, Integer iteration) {
        //XOManagerFactory factory = setUpDB(storeUri);
        if (iteration != null) {
            loadIteration(iteration);
        } else if (benchmarkUrl != null) {
            return runBenchmark(benchmarkUrl);
        } else {
            startNewIteration(configUrl);
        }
        //factory.close();
        return 0d;
    }

    public Double runBenchmark(URL benchmarkUrl) {
        this.activeClassificationConfiguration = readConfiguration(benchmarkUrl);
        this.setUpData();
        this.activeClassificationConfiguration.materialize();
        Set<ComponentDescriptor> reference = this.activeClassificationConfiguration.execute();
        CohesionCriterion cohesionCriterion = new CohesionCriterion();
        Set<ComponentDescriptor> comp = cohesionCriterion.classify(2, null, false,
                this.activeClassificationConfiguration.getOptimization() == ClassificationConfiguration.Optimization.SIMILARITY);
        StringBuilder referenceRsf = new StringBuilder();
        SARFRunner.xoManager.currentTransaction().begin();
        for (ComponentDescriptor componentDescriptor : reference) {
            for (TypeDescriptor typeDescriptor : componentDescriptor.getContainedTypes()) {
                referenceRsf.append("contain " + componentDescriptor.getName() + " " + typeDescriptor.getName() + "\n");
            }
        }
        StringBuilder compRsf = new StringBuilder();
        for (ComponentDescriptor componentDescriptor : comp) {
            for (TypeDescriptor typeDescriptor : componentDescriptor.getContainedTypes()) {
                compRsf.append("contain " + componentDescriptor.getName() + " " + typeDescriptor.getName() + "\n");
            }
        }
        SARFRunner.xoManager.currentTransaction().commit();
        try {
            MoJoCalculator moJoCalculator1 = new MoJoCalculator(
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(referenceRsf.toString().getBytes()))),
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(compRsf.toString().getBytes())))
            );
            MoJoCalculator moJoCalculator2 = new MoJoCalculator(
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(compRsf.toString().getBytes()))),
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(referenceRsf.toString().getBytes())))
            );
            MoJoCalculator moJoFmCalculator = new MoJoCalculator(
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(compRsf.toString().getBytes()))),
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(referenceRsf.toString().getBytes())))
            );
            MoJoCalculator moJoPlusCalculator1 = new MoJoCalculator(
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(referenceRsf.toString().getBytes()))),
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(compRsf.toString().getBytes())))
            );
            MoJoCalculator moJoPlusCalculator2 = new MoJoCalculator(
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(compRsf.toString().getBytes()))),
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(referenceRsf.toString().getBytes())))
            );
            Long mojoCompRef = moJoCalculator1.mojo();
            Long mojoRefComp = moJoCalculator2.mojo();
            Long mojo = Math.min(mojoCompRef, mojoRefComp);
            Double mojoFm = moJoFmCalculator.mojofm();
            Long mojoPlusCompRef = moJoPlusCalculator1.mojoplus();
            Long mojoPlusRefComp = moJoPlusCalculator2.mojoplus();
            Long mojoPlus = Math.min(mojoPlusCompRef, mojoPlusRefComp);
            TypeRepository typeRepository = SARFRunner.xoManager.getRepository(TypeRepository.class);
            Long typeCount = typeRepository.countAllInternalTypes();
            try (FileWriter fW = new FileWriter("Result_Benchmark_" + System.currentTimeMillis())) {
                BufferedWriter bW = new BufferedWriter(fW);
                PrintWriter pW = new PrintWriter(bW);
                pW.println(WeightConstants.stringify());
                pW.println("MoJo Quality: " + (100 - (100. * mojo / typeCount)) + " %");
                pW.println("MoJoFM Quality: " + mojoFm + " %");
                pW.println("MoJo Plus Quality: " + (100 - (100. * mojoPlus / typeCount)) + " %");

                SARFRunner.xoManager.currentTransaction().begin();
                ActiveClassificationConfiguration.prettyPrint(comp, "", pW);
                SARFRunner.xoManager.currentTransaction().commit();
                pW.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return mojoFm;
        } catch (NullPointerException e) {
            e.printStackTrace();
            return Double.MIN_VALUE;
        }
    }

    public void startNewIteration() {

    }

    public void startNewIteration(URL configUrl) {

        if (configUrl != null) {
            this.activeClassificationConfiguration = readConfiguration(configUrl);
        }
        if (configUrl == null) {
            this.activeClassificationConfiguration = new ActiveClassificationConfiguration(1);
        }
        this.activeClassificationConfiguration.addClassificationCriterion(new CohesionCriterion());
        this.setUpData();
        this.activeClassificationConfiguration.materialize();
        this.activeClassificationConfiguration.execute();
    }

    private ActiveClassificationConfiguration readConfiguration(URL configUrl) {
        ActiveClassificationConfiguration activeClassificationConfiguration = null;
        LOG.info("Reading XML Configuration");
        try {
            URL schemaUrl = SARFRunner.class.getClassLoader().getResource("schema.xsd");
            JAXBContext jaxbContext = JAXBContext.newInstance(ActiveClassificationConfiguration.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = sf.newSchema(schemaUrl);
            jaxbUnmarshaller.setSchema(schema);
            LOG.info("Unmarshalling XML Configuration");
            activeClassificationConfiguration = (ActiveClassificationConfiguration) jaxbUnmarshaller.unmarshal(configUrl);
        } catch (JAXBException | SAXException e) {
            LOG.error(e);
        }
        LOG.info("Unmarshalling XML Configuration Successful");
        return activeClassificationConfiguration;
    }

    public ActiveClassificationConfiguration getCurrentIteration() {
        return null;
    }

    public ClassificationConfiguration loadIteration(Integer iteration) {
        return null;
    }

    private void setUpData() {
        ClassificationConfigurationRepository classificationConfigurationRepository = SARFRunner.xoManager.getRepository(ClassificationConfigurationRepository.class);
        if (this.activeClassificationConfiguration.getIteration() == 1) {
            LOG.info("Resetting Data");
            SARFRunner.xoManager.currentTransaction().begin();
            SARFRunner.xoManager.createQuery(
                    "MATCH (sarf:SARF) DETACH DELETE sarf"
            ).execute();
            SARFRunner.xoManager.createQuery(
                    "MATCH ()-[c:COUPLES]-() DELETE c"
            ).execute();
            SARFRunner.xoManager.createQuery(
                    "MATCH ()-[s:IS_SIMILAR_TO]-() DELETE s"
            ).execute();
            SARFRunner.xoManager.currentTransaction().commit();
            SARFRunner.xoManager.currentTransaction().begin();
            LOG.info("Preparing Data Set");
            SARFRunner.xoManager.getRepository(TypeRepository.class).markAllInternalTypes("de.htw");
            SARFRunner.xoManager.currentTransaction().commit();
            TypeCouplingEnricher.enrich();
        } else if (this.activeClassificationConfiguration.getIteration() <= classificationConfigurationRepository.getCurrentConfiguration().getIteration()) {
            LOG.error("Specified Configuration Iteration must be either 1 or " +
                    classificationConfigurationRepository.getCurrentConfiguration().getIteration() + 1);
            System.exit(1);
        }
    }
}
