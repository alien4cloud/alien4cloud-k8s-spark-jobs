#!/bin/bash

export TOSCA_JOB_ID="$(uuidgen)"

CA_CERT_FILE=$(mktemp)
CLIENT_CERT_FILE=$(mktemp)
CLIENT_KEY_FILE=$(mktemp)

PARAM_FILE=$(mktemp)

echo $CA_CERT | base64 -d > $CA_CERT_FILE
echo $CLIENT_CERT | base64 -d > $CLIENT_CERT_FILE
echo $CLIENT_KEY | base64 -d > $CLIENT_KEY_FILE

echo "Submitting JOB_ID: ${TOSCA_JOB_ID}"

# Common Parameters
envsubst > $PARAM_FILE <<EOF
--master
k8s://$SERVER
--deploy-mode
cluster
--name
$NODE
--conf
spark.kubernetes.authenticate.submission.caCertFile=$CA_CERT_FILE
--conf
spark.kubernetes.authenticate.submission.clientKeyFile=$CLIENT_KEY_FILE
--conf
spark.kubernetes.authenticate.submission.clientCertFile=$CLIENT_CERT_FILE
--conf
spark.kubernetes.container.image=$CONTAINER_NAME
--conf
spark.kubernetes.namespace=$NAMESPACE
--conf
spark.executor.instances=$BATCH_SIZE
--conf
spark.kubernetes.authenticate.driver.serviceAccountName=spark-sa
--conf
spark.kubernetes.driver.label.job_id=$TOSCA_JOB_ID
--conf
spark.kubernetes.submission.waitAppCompletion=false
--conf
spark.hadoop.dfs.client.use.datanode.hostname=true
--conf
spark.kubernetes.container.image.pullPolicy=IfNotPresent
EOF

# Output ANNOTATIONSww
echo $ANNOTATIONS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.annotation.\(.key)=\"\(.value)\"","--conf", "spark.kubernetes.executor.annotation.\(.key)=\"\(.value)\""]) | flatten | .[]' >> $PARAM_FILE

# Output ANNOTATIONS
echo $LABELS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.label.\(.key)=\"\(.value)\"","--conf", "spark.kubernetes.executor.label.\(.key)=\"\(.value)\""]) | flatten | .[]' >> $PARAM_FILE

# Output ENVIRONMENTS
echo $ENVIRONMENTS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driverEnv.\(.key)=\(.value)"]) | flatten | .[]' >> $PARAM_FILE

# Output SECRETS
echo $SECRETS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.secrets.\(.key)=\(.value)","--conf", "spark.kubernetes.executor.secrets.\(.key)=\"\(.value)\""]) | flatten | .[]' >> $PARAM_FILE

# Output Mounts
echo $VOLUMES | jq -r 'map(["--conf","spark.kubernetes.executor.volumes.\(.type).\(.name).mount.path=\(.mountPath)","--conf","spark.kubernetes.driver.volumes.\(.type).\(.name).mount.path=\(.mountPath)"]) | flatten | .[]' >> $PARAM_FILE

# Output Mounts options
echo $VOLUMES | jq -r  '.[] | .name as $n | .type as $t | select(has("options")) | .options |to_entries | .[] | { name: $n , type: $t,key: .key , val: .value} | [ "--conf" , "spark.kubernetes.driver.volumes.\(.type).\(.name).options.\(.key)=\(.val)", "--conf", "spark.kubernetes.executor.volumes.\(.type).\(.name).options.\(.key)=\(.val)" ] | .[]' >> $PARAM_FILE

if [ ! -z "$EXECUTOR_LIMIT_CORES" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.executor.limit.cores=${EXECUTOR_LIMIT_CORES}
EOF
fi

if [ ! -z "$DRIVER_LIMIT_CORES" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.driver.limit.cores=${DRIVER_LIMIT_CORES}
EOF
fi

if [ ! -z "$EXECUTOR_REQUEST_CORES" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.executor.request.cores=${EXECUTOR_REQUEST_CORES}
EOF
fi

if [ ! -z "$DRIVER_MEMORY" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.driver.memory=${DRIVER_MEMORY}
EOF
fi

if [ ! -z "$EXECUTOR_MEMORY" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.executor.memory=${EXECUTOR_MEMORY}
EOF
fi

if [ ! -z "$DRIVER_MEMORY_OVERHEAD" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.driver.memoryOverhead=${DRIVER_MEMORY_OVERHEAD}
EOF
fi

if [ ! -z "$EXECUTOR_MEMORY_OVERHEAD" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.executor.memoryOverhead=${EXECUTOR_MEMORY_OVERHEAD}
EOF
fi

if [ ! -z "$MEMORY_OVERHEAD_FACTOR" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.memoryOverheadFactor=${MEMORY_OVERHEAD_FACTOR}
EOF
fi

if [ ! -z "$CONTEXT" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.context=${CONTEXT}
EOF
fi

if [ ! -z "$DRIVER_MASTER" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.driver.master=${DRIVER_MASTER}
EOF
fi



# Output ANNOTATIONS_SERVICE_DRIVER
echo $ANNOTATIONS_SERVICE_DRIVER | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.service.annotation.\(.key)=\"\(.value)\"","--conf", "spark.kubernetes.executor.annotation.\(.key)=\"\(.value)\""]) | flatten | .[]' >> $PARAM_FILE

if [ ! -z "$DRIVER_REQUEST_CORES" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.driver.request.cores=${DRIVER_REQUEST_CORES}
EOF
fi

if [ ! -z "$LOCAL_DIRS_TMPFS" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.local.dirs.tmpfs=${LOCAL_DIRS_TMPFS}
EOF
fi

if [ ! -z "$KERBEROS_KRB5_PATH" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.kerberos.krb5.path=${KERBEROS_KRB5_PATH}
EOF
fi

if [ ! -z "$KERBEROS_KRB5_CONFIG_MAP_NAME" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.kerberos.krb5.configMapName=${KERBEROS_KRB5_CONFIG_MAP_NAME}
EOF
fi

if [ ! -z "$HADOOP_CONFIG_MAP_NAME" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.hadoop.configMapName=${HADOOP_CONFIG_MAP_NAME}
EOF
fi

if [ ! -z "$KERBEROS_TOKEN_SECRET_NAME" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.kerberos.tokenSecret.name=${KERBEROS_TOKEN_SECRET_NAME}
EOF
fi

if [ ! -z "$KERBEROS_TOKEN_ITEM_KEY" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.kerberos.tokenSecret.itemKey=${KERBEROS_TOKEN_ITEM_KEY}
EOF
fi

if [ ! -z "$DRIVER_POD_TEMPLATE_CONTAINER_NAME" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.driver.podTemplateContainerName=${DRIVER_POD_TEMPLATE_CONTAINER_NAME}
EOF
fi

if [ ! -z "$EXECUTOR_POD_TEMPLATE_CONTAINER_NAME" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.executor.podTemplateContainerName=${EXECUTOR_POD_TEMPLATE_CONTAINER_NAME}
EOF
fi

if [ ! -z "$EXECUTOR_DELETE_ON_TERMINATION" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.executor.deleteOnTermination=${EXECUTOR_DELETE_ON_TERMINATION}
EOF
fi

if [ ! -z "$SUBMISSION_CONNECTION_TIMEOUT" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.submission.connectionTimeout=${SUBMISSION_CONNECTION_TIMEOUT}
EOF
fi

if [ ! -z "$SUBMISSION_REQUEST_TIMEOUT" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.submission.requestTimeout=${SUBMISSION_REQUEST_TIMEOUT}
EOF
fi

if [ ! -z "$DRIVER_CONNECTION_TIMEOUT" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.driver.connectionTimeout=${DRIVER_CONNECTION_TIMEOUT}
EOF
fi

if [ ! -z "$DRIVER_REQUEST_TIMEOUT" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.driver.requestTimeout=${DRIVER_REQUEST_TIMEOUT}
EOF
fi

if [ ! -z "$APP_KILL_POD_DELETION_GRACE_PERIOD" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.appKillPodDeletionGracePeriod=${APP_KILL_POD_DELETION_GRACE_PERIOD}
EOF
fi

if [ ! -z "$FILE_UPLOAD_PATH" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.file.upload.path=${FILE_UPLOAD_PATH}
EOF
fi

if [ ! -z "$POD_DRIVER_TEMPLATE" ]
  then
    if [ -s $POD_DRIVER_TEMPLATE ]
      then
        echo "Pod template driver is not empty"
    else
      POD_DRIVER_TEMPLATE=""
      echo "Pod template driver is empty"
    fi
fi



if [ ! -z "$POD_DRIVER_TEMPLATE" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.driver.podTemplateFile=${POD_DRIVER_TEMPLATE}
EOF
fi

if [ ! -z "$POD_EXECUTOR_TEMPLATE" ]
  then
    if [ -s $POD_EXECUTOR_TEMPLATE ]
      then
        echo "Pod template executor is not empty"
    else
      POD_EXECUTOR_TEMPLATE=""
      echo "Pod template executor is  empty"
    fi
fi



if [ ! -z "$POD_EXECUTOR_TEMPLATE" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.executor.podTemplateFile=${POD_EXECUTOR_TEMPLATE}
EOF
fi


function pre_submit() {
  # Add Additionnal options if any
  # Add Parameters from parameter file
  if [ -f $PARAMETER_FILE ]; then
    cat $PARAMETER_FILE >> $PARAM_FILE
  fi
}

function do_submit() {

  # Add Parameters from properties
  echo $PARAMETERS | jq -r '.[]' >> $PARAM_FILE

  submitCmd="spark-submit $(cat $PARAM_FILE | tr '\n' ' ')"

  if [ "$debug_operations" == "true" ]; then
    echo "PARAM_FILE is $PARAM_FILE"
    #cat $PARAM_FILE
    echo "submit command : $submitCmd"
  fi
  eval "$submitCmd"
  #cat $PARAM_FILE | tr '\n' ' ' | xargs -0 spark-submit
}

function do_cleanup() {
  if [ "$debug_operations" != "true" ]; then
    rm -f $CA_CERT_FILE
    rm -f $CLIENT_CERT_FILE
    rm -f $CLIENT_KEY_FILE
    rm -f $PARAM_FILE
  fi
}
