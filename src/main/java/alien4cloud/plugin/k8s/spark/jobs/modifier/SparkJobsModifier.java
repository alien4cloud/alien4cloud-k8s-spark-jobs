package alien4cloud.plugin.k8s.spark.jobs.modifier;

import alien4cloud.paas.wf.TopologyContext;
import alien4cloud.paas.wf.WorkflowSimplifyService;
import alien4cloud.paas.wf.WorkflowsBuilderService;
import alien4cloud.paas.wf.validation.WorkflowValidator;
import alien4cloud.plugin.k8s.spark.jobs.model.K8sCluster;
import alien4cloud.plugin.k8s.spark.jobs.model.K8sConfig;
import alien4cloud.plugin.k8s.spark.jobs.model.K8sContext;
import alien4cloud.plugin.k8s.spark.jobs.model.K8sUser;
import alien4cloud.plugin.k8s.spark.jobs.utils.K8sConfigParser;
import alien4cloud.tosca.context.ToscaContext;
import alien4cloud.tosca.context.ToscaContextual;
import alien4cloud.tosca.parser.ToscaParser;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.AbstractToscaType;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component("k8s-spark-jobs-modifier")
public class SparkJobsModifier extends TopologyModifierSupport {


    public static final String K8S_TYPES_KUBE_CLUSTER = "org.alien4cloud.kubernetes.api.types.nodes.KubeCluster";
    public static final String K8S_TYPES_SPARK_JOBS = "org.alien4cloud.k8s.spark.jobs.AbstractSparkJob";

    public static final String K8S_TYPES_SIMPLE_RESOURCE = "org.alien4cloud.kubernetes.api.types.SimpleResource";

    public static final String NAMESPACE_RESOURCE_NAME = "NamespaceManager";

    public static final String ROLE_NAME="SparkRole";
    public static final String SERVICEACCOUNT_NAME="SparkSA";
    public static final String ROLEBINDING_NAME="SparkRB";

    public static final String K8S_CSAR_VERSION = "3.0.0-SNAPSHOT";

    @Inject
    private K8sConfigParser configParser;

    @Inject
    private WorkflowSimplifyService workflowSimplifyService;

    @Inject
    private WorkflowsBuilderService workflowBuilderService;

    /**
     * Yaml For Role Creation
     */
    private String yamlRole;

    /**
     * Yaml For ServiceAccount Creation
     */
    private String yamlServiceAccount;

    /**
     * Yaml for Rolebinding Creation
     */
    private String yamlRoleBinding;

    @PostConstruct
    private void init() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();

        Resource resourceRole = resourceLoader.getResource("classpath:k8s/role.yml");
        Resource resourceSA = resourceLoader.getResource("classpath:k8s/serviceaccount.yml");
        Resource resourceRB = resourceLoader.getResource("classpath:k8s/rolebinding.yml");

        try {
            yamlRole = IOUtils.toString(resourceRole.getInputStream());
            yamlServiceAccount = IOUtils.toString(resourceSA.getInputStream());
            yamlRoleBinding = IOUtils.toString(resourceRB.getInputStream());
        } catch (IOException e) {
            log.error("Can't Read K8S Resources");
        }
    }

    @Override
    @ToscaContextual
    public void process(Topology topology, FlowExecutionContext context) {
        try {
            WorkflowValidator.disableValidationThreadLocal.set(true);
            doProcess(topology, context);

            TopologyContext topologyContext = workflowBuilderService.buildCachedTopologyContext(new TopologyContext() {
                @Override
                public String getDSLVersion() {
                    return ToscaParser.LATEST_DSL;
                }

                @Override
                public Topology getTopology() {
                    return topology;
                }

                @Override
                public <T extends AbstractToscaType> T findElement(Class<T> clazz, String elementId) {
                    return ToscaContext.get(clazz, elementId);
                }
            });

            workflowSimplifyService.reentrantSimplifyWorklow(topologyContext, topology.getWorkflows().keySet());
        } catch (Exception e) {
            log.warn("Can't process k8s-spark-jobs modifier:", e);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    protected void doProcess(Topology topology,FlowExecutionContext context) {
        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());;

        String k8sYamlConfig = (String) context.getExecutionCache().get(K8S_TYPES_KUBE_CLUSTER);
        String nsNodeName = (String) context.getExecutionCache().get(NAMESPACE_RESOURCE_NAME);

        K8sConfig k8sConfig = configParser.parse(k8sYamlConfig);

        Optional<K8sContext> k8sContext = k8sConfig.getContext(k8sConfig.getCurrentContext());
        if (!k8sContext.isPresent()) {
            log.error("Invalid k8s configuration");
            return;
        }

        Optional<K8sCluster> k8sCluster = k8sConfig.getCluster(k8sContext.get().getCluster());
        Optional<K8sUser> k8sUser = k8sConfig.getUser(k8sContext.get().getUser());
        if ((!k8sCluster.isPresent())||(!k8sUser.isPresent())) {
            log.error("Invalid k8s configuration");
            return;
        }

        Set<NodeTemplate> jobs = TopologyNavigationUtil.getNodesOfType(topology, K8S_TYPES_SPARK_JOBS, true);

        // Add Resources
        if (nsNodeName != nulll && jobs.size() > 0) {
            // When No Namespace Manager, role sa and rb must be preconfigured
            addRole(csar, topology, nsNodeName, k8sYamlConfig);
            addServiceAccount(csar, topology, nsNodeName, k8sYamlConfig);
            addRoleBinding(csar, topology, nsNodeName, k8sYamlConfig, k8sContext.get().getNamespace());
        }

        jobs.forEach(job -> manageJob(job,csar,topology,k8sContext.get(),k8sCluster.get(),k8sUser.get(),nsNodeName));
    }

    protected void manageJob(NodeTemplate job,Csar csar,Topology topology,K8sContext k8sContext,K8sCluster k8sCluster,K8sUser k8sUser,String nsNodeName) {
        // Inject Kube Configuration in SparkJob Nodes
        if (StringUtils.isNotEmpty(k8sContext.getNamespace())) {
            setNodePropertyPathValue(csar, topology, job, "namespace", new ScalarPropertyValue(k8sContext.getNamespace()));
        }

        if (StringUtils.isNotEmpty(k8sCluster.getServer())) {
            setNodePropertyPathValue(csar, topology, job, "server", new ScalarPropertyValue(k8sCluster.getServer()));
        }

        if (StringUtils.isNotEmpty(k8sCluster.getCertificateAuthorityData())) {
            setNodePropertyPathValue(csar, topology, job, "ca_cert", new ScalarPropertyValue(k8sCluster.getCertificateAuthorityData()));
        }

        if (StringUtils.isNotEmpty(k8sUser.getClientCertificateData())) {
            setNodePropertyPathValue(csar, topology, job, "client_cert", new ScalarPropertyValue(k8sUser.getClientCertificateData()));
        }

        if (StringUtils.isNotEmpty(k8sUser.getClientKeyData())) {
            setNodePropertyPathValue(csar, topology, job, "client_key", new ScalarPropertyValue(k8sUser.getClientKeyData()));
        }

        // Add a dependency relation to namespace node if any
        if (StringUtils.isNotEmpty(nsNodeName)) {
            addRelationshipTemplate(csar,topology,job,nsNodeName,NormativeRelationshipConstants.DEPENDS_ON,"dependency", "feature");
        }
    }

    private String getK8SCsarVersion(Topology topology) {
        for (CSARDependency dep : topology.getDependencies()) {
            if (dep.getName().equals("org.alien4cloud.kubernetes.api")) {
                return dep.getVersion();
            }
        }
        return K8S_CSAR_VERSION;
    }

    protected void addRole(Csar csar,Topology topology,String nsNodeName,String k8sYamlConfig) {
        NodeTemplate role = addNodeTemplate(csar, topology, ROLE_NAME, K8S_TYPES_SIMPLE_RESOURCE, getK8SCsarVersion(topology));

        setNodePropertyPathValue(csar,topology,role,"resource_type",new ScalarPropertyValue("role"));
        setNodePropertyPathValue(csar,topology,role,"resource_id",new ScalarPropertyValue("spark-role"));
        setNodePropertyPathValue(csar,topology,role,"resource_spec",new ScalarPropertyValue(yamlRole));

        setNodePropertyPathValue(csar, topology, role, "kube_config" ,new ScalarPropertyValue(k8sYamlConfig));

        // Add a dependency relation to namespace node if any
        if (StringUtils.isNotEmpty(nsNodeName)) {
            addRelationshipTemplate(csar,topology,role,nsNodeName,NormativeRelationshipConstants.DEPENDS_ON,"dependency", "feature");
        }
    }

    protected void addServiceAccount(Csar csar,Topology topology,String nsNodeName,String k8sYamlConfig) {
        NodeTemplate sa = addNodeTemplate(csar, topology, SERVICEACCOUNT_NAME, K8S_TYPES_SIMPLE_RESOURCE, getK8SCsarVersion(topology));

        setNodePropertyPathValue(csar,topology,sa,"resource_type",new ScalarPropertyValue("serviceaccount"));
        setNodePropertyPathValue(csar,topology,sa,"resource_id",new ScalarPropertyValue("spark-sa"));
        setNodePropertyPathValue(csar,topology,sa,"resource_spec",new ScalarPropertyValue(yamlServiceAccount));

        setNodePropertyPathValue(csar, topology, sa, "kube_config" ,new ScalarPropertyValue(k8sYamlConfig));

        // Add a dependency relation to namespace node if any
        if (StringUtils.isNotEmpty(nsNodeName)) {
            addRelationshipTemplate(csar,topology,sa,nsNodeName,NormativeRelationshipConstants.DEPENDS_ON,"dependency", "feature");
        }
    }

    protected void addRoleBinding(Csar csar,Topology topology,String nsNodeName,String k8sYamlConfig,String namespace) {
        NodeTemplate sa = addNodeTemplate(csar, topology, ROLEBINDING_NAME, K8S_TYPES_SIMPLE_RESOURCE, getK8SCsarVersion(topology));

        String yamlResource = String.format("%s\n  namespace: %s\n",yamlRoleBinding,namespace);

        setNodePropertyPathValue(csar,topology,sa,"resource_type",new ScalarPropertyValue("rolebinding"));
        setNodePropertyPathValue(csar,topology,sa,"resource_id",new ScalarPropertyValue("spark-rb"));
        setNodePropertyPathValue(csar,topology,sa,"resource_spec",new ScalarPropertyValue(yamlResource));

        setNodePropertyPathValue(csar, topology, sa, "kube_config" ,new ScalarPropertyValue(k8sYamlConfig));

        // Add a dependency relation to namespace node if any
        if (StringUtils.isNotEmpty(nsNodeName)) {
            addRelationshipTemplate(csar,topology,sa,nsNodeName,NormativeRelationshipConstants.DEPENDS_ON,"dependency", "feature");
        }
    }
}
