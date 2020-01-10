package alien4cloud.plugin.k8s.spark.jobs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Optional;

@ToString
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class K8sConfig {

    private List<K8sNamedContext> contexts;

    private List<K8sNamedCluster>  clusters;

    private List<K8sNamedUser> users;

    @JsonProperty("current-context")
    private String currentContext;

    public Optional<K8sContext> getContext(String name) {
       Optional<K8sNamedContext> ctx = contexts.stream().filter(c -> c.getName().equals(name)).findFirst();
       if (ctx.isPresent()) {
           return Optional.of(ctx.get().getContext());
       } else {
           return Optional.empty();
       }
    }

    public Optional<K8sCluster> getCluster(String name) {
        Optional<K8sNamedCluster> ctx = clusters.stream().filter(c -> c.getName().equals(name)).findFirst();
        if (ctx.isPresent()) {
            return Optional.of(ctx.get().getCluster());
        } else {
            return Optional.empty();
        }
    }

    public Optional<K8sUser> getUser(String name) {
        Optional<K8sNamedUser> ctx = users.stream().filter(c -> c.getName().equals(name)).findFirst();
        if (ctx.isPresent()) {
            return Optional.of(ctx.get().getUser());
        } else {
            return Optional.empty();
        }
    }

}
