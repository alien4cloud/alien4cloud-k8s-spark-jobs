package alien4cloud.plugin.k8s.spark.jobs.model;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
public class K8sNamedUser {

    private String name;

    private K8sUser user;
}
