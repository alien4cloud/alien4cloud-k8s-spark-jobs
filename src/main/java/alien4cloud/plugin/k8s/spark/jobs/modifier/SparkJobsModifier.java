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
import alien4cloud.utils.PropertyUtil;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.TopologyModifierSupport;
import org.alien4cloud.plugin.kubernetes.csar.Version;
import org.alien4cloud.tosca.exceptions.InvalidPropertyValueException;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ComplexPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.alien4cloud.tosca.model.types.AbstractToscaType;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.normative.constants.NormativeRelationshipConstants;
import org.alien4cloud.tosca.normative.primitives.Size;
import org.alien4cloud.tosca.normative.primitives.SizeUnit;
import org.alien4cloud.tosca.normative.types.SizeType;
import org.alien4cloud.tosca.utils.TopologyNavigationUtil;
import org.alien4cloud.tosca.utils.ToscaTypeUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.google.common.collect.Maps;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component("k8s-spark-jobs-modifier")
public class SparkJobsModifier extends TopologyModifierSupport {

    // TODO: use constant from K8S plugin
    public static final String K8S_TYPES_KUBE_CLUSTER = "org.alien4cloud.kubernetes.api.types.nodes.KubeCluster";
    public static final String K8S_TYPES_PV = "org.alien4cloud.kubernetes.api.types.PersistentVolume";
    public static final String K8S_TYPES_SPARK_JOBS = "org.alien4cloud.k8s.spark.jobs.AbstractSparkJob";

    public static final String K8S_SPARKJOBS_TYPES_VOLUMES_CLAIM    = "org.alien4cloud.k8s.spark.jobs.PersistentVolumeClaimSource";
    public static final String K8S_SPARKJOBS_TYPES_VOLUMES_CLAIM_SC = "org.alien4cloud.k8s.spark.jobs.PersistentVolumeClaimStorageClassSource";

    public static final String K8S_TYPES_SIMPLE_RESOURCE = "org.alien4cloud.kubernetes.api.types.SimpleResource";

    public static final String NAMESPACE_RESOURCE_NAME = "NamespaceManager";

    public static final String ROLE_NAME="SparkRole";
    public static final String SERVICEACCOUNT_NAME="SparkSA";
    public static final String ROLEBINDING_NAME="SparkRB";

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
        } catch (Exception e) {
            log.warn("Can't process k8s-spark-jobs modifier:", e);
        } finally {
            WorkflowValidator.disableValidationThreadLocal.remove();
        }
    }

    protected boolean doProcess(Topology topology,FlowExecutionContext context) {
        Csar csar = new Csar(topology.getArchiveName(), topology.getArchiveVersion());;

        Set<NodeTemplate> jobs = TopologyNavigationUtil.getNodesOfType(topology, K8S_TYPES_SPARK_JOBS, true);

        String k8sYamlConfig = (String) context.getExecutionCache().get(K8S_TYPES_KUBE_CLUSTER);
        String nsNodeName = (String) context.getExecutionCache().get(NAMESPACE_RESOURCE_NAME);

        if (jobs.size()==0) {
            // No spark jobs, nothing to do
            if (log.isDebugEnabled()) {
                log.debug("No spark Job detected, nothin to do");
            }
            return false;
        }

        K8sConfig k8sConfig = null;

        if (k8sYamlConfig != null) {
            k8sConfig = configParser.parse(k8sYamlConfig);
        } else {
            log.error("No Kubernetes config found");
            context.log().error("No Kubernetes config found");
            return false;
        }

        Optional<K8sContext> k8sContext = k8sConfig.getContext(k8sConfig.getCurrentContext());
        if (!k8sContext.isPresent()) {
            log.error("Invalid k8s configuration");
            context.log().error("Invalid k8s configuration");
            return false;
        }

        Optional<K8sCluster> k8sCluster = k8sConfig.getCluster(k8sContext.get().getCluster());
        Optional<K8sUser> k8sUser = k8sConfig.getUser(k8sContext.get().getUser());
        if ((!k8sCluster.isPresent())||(!k8sUser.isPresent())) {
            log.error("Invalid k8s configuration");
            context.log().error("Invalid k8s configuration");
            return false;
        }

        // Add Resources
        if (nsNodeName != null) {
            // When No Namespace Manager, role sa and rb must be preconfigured
            addRole(csar, topology, nsNodeName, k8sYamlConfig, k8sContext.get().getNamespace());
            addServiceAccount(csar, topology, nsNodeName, k8sYamlConfig, k8sContext.get().getNamespace());
            addRoleBinding(csar, topology, nsNodeName, k8sYamlConfig, k8sContext.get().getNamespace());
        }

        jobs.forEach(job -> manageJob(job,csar,topology,k8sContext.get(),k8sCluster.get(),k8sUser.get(),nsNodeName));

        var pvcnodes = TopologyNavigationUtil.getNodesOfType(topology, K8S_SPARKJOBS_TYPES_VOLUMES_CLAIM, true);
        pvcnodes.forEach (pvc -> managePVC (pvc, csar, topology, nsNodeName, k8sYamlConfig, k8sContext.get().getNamespace()));

        return true;
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

        // add label for ALIEN-3696
        addLabelOnSparkJob (job, "clusterPolicy", "privileged");
    }

    private void managePVC (NodeTemplate pvc,Csar csar,Topology topology,String nsNodeName,String k8sYamlConfig,String namespace) {
       log.debug ("Processing PVC {}", pvc.getName());
       AbstractPropertyValue claimNamePV = PropertyUtil.getPropertyValueFromPath(pvc.getProperties(), "spec.claimName");
       if (claimNamePV == null) {
          log.debug ("Adding node {} named {}", K8S_TYPES_SIMPLE_RESOURCE, pvc.getName() + "_PVC");
          NodeTemplate volumeClaimResource = addNodeTemplate(csar,topology, pvc.getName() + "_PVC", K8S_TYPES_SIMPLE_RESOURCE, getK8SCsarVersion(topology));
          String claimName = pvc.getName().toLowerCase() + "-pvc";
          setNodePropertyPathValue(csar,topology,volumeClaimResource,"resource_type",new ScalarPropertyValue("pvc"));
          setNodePropertyPathValue(csar,topology,volumeClaimResource,"resource_id",new ScalarPropertyValue(claimName));

          String spec = "kind: PersistentVolumeClaim\n" +
                        "apiVersion: v1\n" +
                        "metadata:\n" +
                        "  name: " + claimName + "\n" +
                        "spec:\n" +
                        "  accessModes:\n" +
                        "    - " + PropertyUtil.getScalarValue(pvc.getProperties().get("accessModes")) + "\n" +
                        "  resources:\n" +
                        "    requests:\n" +
                        "      storage: " + parseSize(PropertyUtil.getScalarValue(pvc.getProperties().get("size"))) + "\n";
          NodeType volumeNodeType = ToscaContext.get(NodeType.class, pvc.getType());
          if (ToscaTypeUtils.isOfType(volumeNodeType, K8S_SPARKJOBS_TYPES_VOLUMES_CLAIM_SC)) {
             // add the storage class name to the claim
             String storageClassName = PropertyUtil.getScalarValue(pvc.getProperties().get("storageClassName"));
             spec += "  storageClassName: " + storageClassName + "\n";
          }
          AbstractPropertyValue selectorProperty = PropertyUtil.getPropertyValueFromPath(pvc.getProperties(), "selector");
          if ((selectorProperty != null) && (selectorProperty instanceof ComplexPropertyValue)) {
             var currentMap = ((ComplexPropertyValue) selectorProperty).getValue();
             var labels = (Map<String,Object>)currentMap.get("matchLabels");
             var label = labels.entrySet().iterator().next();
             Object value = label.getValue();
             String sValue = (value instanceof ScalarPropertyValue ? ((ScalarPropertyValue)value).getValue() : (String)value);
             spec += "  selector:\n    matchLabels:\n      " + label.getKey() + ": " + sValue + "\n";

             log.debug ("Adding node {} named {}", K8S_TYPES_PV, pvc.getName() + "_Volume");
             NodeTemplate volumeResource = addNodeTemplate(csar, topology, pvc.getName() + "_Volume", K8S_TYPES_PV, getK8SCsarVersion(topology));
             setNodePropertyPathValue(csar, topology, volumeResource, "kube_config" ,new ScalarPropertyValue(k8sYamlConfig));
             volumeResource.getProperties().put("label_name", new ScalarPropertyValue(label.getKey()));
             volumeResource.getProperties().put("label_value", new ScalarPropertyValue(sValue));
             if (StringUtils.isNotEmpty(namespace)) {
                addRelationshipTemplate(csar, topology, topology.getNodeTemplates().get(NAMESPACE_RESOURCE_NAME),
                                        volumeResource.getName(), NormativeRelationshipConstants.DEPENDS_ON, "dependency", "feature");
             } else {
                addRelationshipTemplate(csar, topology, volumeClaimResource,
                                        volumeResource.getName(), NormativeRelationshipConstants.DEPENDS_ON, "dependency", "feature");
             }
          }
          log.debug ("PVC spec is {}", spec);
          setNodePropertyPathValue(csar,topology,volumeClaimResource,"resource_spec",new ScalarPropertyValue(spec));

          setNodePropertyPathValue(csar, topology, volumeClaimResource, "kube_config" ,new ScalarPropertyValue(k8sYamlConfig));

          if (StringUtils.isNotEmpty(namespace)) {
             setNodePropertyPathValue(csar, topology, volumeClaimResource, "namespace", new ScalarPropertyValue(namespace));
          }

          // Add a dependency relation to namespace node if any
          if (StringUtils.isNotEmpty(nsNodeName)) {
             addRelationshipTemplate(csar,topology,volumeClaimResource,nsNodeName,NormativeRelationshipConstants.DEPENDS_ON,"dependency", "feature");
          }

          var relationships = TopologyNavigationUtil.getTargetRelationships(pvc, "attachment");
          relationships.forEach(relationshipTemplate -> manageVolumeAttachment(csar, topology, pvc, relationshipTemplate, claimName));
      }

    }

    private String parseSize(String value) {
       SizeType sizeType = new SizeType();
       try {
           Size size = sizeType.parse(value);
           Double d = size.convert(SizeUnit.B.toString());
           return String.valueOf(d.longValue());
       } catch (InvalidPropertyValueException e) {
           return value;
       }
    }

    private void manageVolumeAttachment(Csar csar, Topology topology, NodeTemplate pvc, RelationshipTemplate relationshipTemplate, String claimName) {
        NodeTemplate jobNode = topology.getNodeTemplates().get(relationshipTemplate.getTarget());
        AbstractPropertyValue mountPath = PropertyUtil.getPropertyValueFromPath(relationshipTemplate.getProperties(), "container_path");

        // get the volume name
        AbstractPropertyValue name = PropertyUtil.getPropertyValueFromPath(pvc.getProperties(), "name");
        if (mountPath != null && name != null) {
            ComplexPropertyValue volumeMount = new ComplexPropertyValue();
            volumeMount.setValue(Maps.newHashMap());
            volumeMount.getValue().put("name", name);
            volumeMount.getValue().put("type", "persistentVolumeClaim");
            volumeMount.getValue().put("mountPath", mountPath);
            volumeMount.getValue().put("options", Maps.newHashMap());
            ((Map)volumeMount.getValue().get("options")).put("claimName", claimName);

            List volumes = new ArrayList();
            AbstractPropertyValue volumesPV = jobNode.getProperties().get("volumes");
            if ((volumesPV != null) && (volumesPV instanceof ListPropertyValue)) {
               volumes = ((ListPropertyValue)volumesPV).getValue(); 
            }
            volumes.add(volumeMount);
            jobNode.getProperties().put("volumes", new ListPropertyValue(volumes));
        }
    }

    private void addLabelOnSparkJob(NodeTemplate job,String key,String value) {
        if (log.isDebugEnabled()) {
            log.debug("Adding Label {} = {} on {}",key,value,job.getName());
        }
        ComplexPropertyValue prop = (ComplexPropertyValue) job.getProperties().computeIfAbsent("labels",k -> {
            ComplexPropertyValue p = new ComplexPropertyValue();
            p.setValue(Maps.newHashMap());
            return p;
        });
        prop.getValue().put(key,value);
    }

    private String getK8SCsarVersion(Topology topology) {
        for (CSARDependency dep : topology.getDependencies()) {
            if (dep.getName().equals("org.alien4cloud.kubernetes.api")) {
                return dep.getVersion();
            }
        }
        return Version.K8S_CSAR_VERSION;
    }

    protected void addRole(Csar csar,Topology topology,String nsNodeName,String k8sYamlConfig,String namespace) {
        NodeTemplate role = addNodeTemplate(csar, topology, ROLE_NAME, K8S_TYPES_SIMPLE_RESOURCE, getK8SCsarVersion(topology));

        setNodePropertyPathValue(csar,topology,role,"resource_type",new ScalarPropertyValue("role"));
        setNodePropertyPathValue(csar,topology,role,"resource_id",new ScalarPropertyValue("spark-role"));
        setNodePropertyPathValue(csar,topology,role,"resource_spec",new ScalarPropertyValue(yamlRole));

        setNodePropertyPathValue(csar, topology, role, "kube_config" ,new ScalarPropertyValue(k8sYamlConfig));

        if (StringUtils.isNotEmpty(namespace)) {
            setNodePropertyPathValue(csar, topology, role, "namespace", new ScalarPropertyValue(namespace));
        }

        // Add a dependency relation to namespace node if any
        if (StringUtils.isNotEmpty(nsNodeName)) {
            addRelationshipTemplate(csar,topology,role,nsNodeName,NormativeRelationshipConstants.DEPENDS_ON,"dependency", "feature");
        }
    }

    protected void addServiceAccount(Csar csar,Topology topology,String nsNodeName,String k8sYamlConfig,String namespace) {
        NodeTemplate sa = addNodeTemplate(csar, topology, SERVICEACCOUNT_NAME, K8S_TYPES_SIMPLE_RESOURCE, getK8SCsarVersion(topology));

        setNodePropertyPathValue(csar,topology,sa,"resource_type",new ScalarPropertyValue("serviceaccount"));
        setNodePropertyPathValue(csar,topology,sa,"resource_id",new ScalarPropertyValue("spark-sa"));
        setNodePropertyPathValue(csar,topology,sa,"resource_spec",new ScalarPropertyValue(yamlServiceAccount));

        setNodePropertyPathValue(csar, topology, sa, "kube_config" ,new ScalarPropertyValue(k8sYamlConfig));

        if (StringUtils.isNotEmpty(namespace)) {
            setNodePropertyPathValue(csar, topology, sa, "namespace", new ScalarPropertyValue(namespace));
        }

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

        if (StringUtils.isNotEmpty(namespace)) {
            setNodePropertyPathValue(csar, topology, sa, "namespace", new ScalarPropertyValue(namespace));
        }

        // Add a dependency relation to namespace node if any
        if (StringUtils.isNotEmpty(nsNodeName)) {
            addRelationshipTemplate(csar,topology,sa,nsNodeName,NormativeRelationshipConstants.DEPENDS_ON,"dependency", "feature");
        }
    }
}
