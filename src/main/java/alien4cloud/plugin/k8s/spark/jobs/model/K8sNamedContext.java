package alien4cloud.plugin.k8s.spark.jobs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class K8sNamedContext {
    private String name;

    private K8sContext context;
}
