#!/bin/bash

export TOSCA_JOB_ID="$(uuidgen)"

CA_CERT_FILE=$(mktemp)
CLIENT_CERT_FILE=$(mktemp)
CLIENT_KEY_FILE=$(mktemp)

ADD_OPTS_FILE="${PARAMETER_FILE}_ADDOPTS"
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

# Output ANNOTATIONS
echo $ANNOTATIONS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.annotation.\(.key)=\(.value)","--conf", "spark.kubernetes.executor.annotation.\(.key)=\(.value)"]) | flatten | .[]' >> $PARAM_FILE

# Output ANNOTATIONS
echo $LABELS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.label.\(.key)=\(.value)","--conf", "spark.kubernetes.executor.label.\(.key)=\(.value)"]) | flatten | .[]' >> $PARAM_FILE

# Output ENVIRONMENTS
echo $ENVIRONMENTS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driverEnv.\(.key)=\(.value)"]) | flatten | .[]' >> $PARAM_FILE

# Output SECRETS
echo $SECRETS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.secrets.\(.key)=\(.value)","--conf", "spark.kubernetes.executor.secrets.\(.key)=\(.value)"]) | flatten | .[]' >> $PARAM_FILE

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

if [ ! -z "$MEMORY_OVERHEAD_FACTOR" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.memoryOverheadFactor=${MEMORY_OVERHEAD_FACTOR}
EOF
fi

function pre_submit() {
  # Add Additionnal options if any
  if [ -f $ADD_OPTS_FILE ]; then
	cat $ADD_OPTS_FILE >> $PARAM_FILE
  fi
}

function do_submit() {
  # Add Parameters from parameter file
  if [ -f $PARAMETER_FILE ]; then
    cat $PARAMETER_FILE >> $PARAM_FILE
  fi

  # Add Parameters from properties
  echo $PARAMETERS | jq -r '.[]' >> $PARAM_FILE
  cat $PARAM_FILE | tr '\n' '\0' | xargs -0 spark-submit
}

function do_cleanup() {
  rm $CA_CERT_FILE
  rm $CLIENT_CERT_FILE
  rm $CLIENT_KEY_FILE
  rm $PARAM_FILE
}
