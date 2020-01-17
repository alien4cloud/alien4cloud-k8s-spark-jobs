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
EOF

# Output ANNOTATIONS
echo $ANNOTATIONS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.annotation.\(.key)=\(.value)","--conf", "spark.kubernetes.executor.annotation.\(.key)=\(.value)"]) | flatten | .[]' >> $PARAM_FILE

# Output ANNOTATIONS
echo $LABELS | jq -r 'to_entries | map(["--conf", "spark.kubernetes.driver.label.\(.key)=\(.value)","--conf", "spark.kubernetes.executor.label.\(.key)=\(.value)"]) | flatten | .[]' >> $PARAM_FILE

# Output Mounts
echo $VOLUMES | jq -r 'map(["--conf","spark.kubernetes.executor.volumes.\(.type).\(.name).mount.path=\(.mountPath)","--conf","spark.kubernetes.driver.volumes.\(.type).\(.name).mount.path=\(.mountPath)"]) | flatten | .[]' >> $PARAM_FILE

# Output Mounts options
echo $VOLUMES | jq -r  '.[] | .name as $n | .type as $t | select(has("options")) | .options |to_entries | .[] | { name: $n , type: $t,key: .key , val: .value} | [ "--conf" , "spark.kubernetes.driver.volumes.\(.type).\(.name).options.\(.key)=\(.val)", "--conf", "spark.kubernetes.executor.volumes.\(.type).\(.name).options.\(.key)=\(.val)" ] | .[]' >> $PARAM_FILE

function do_submit() {
  cat $PARAM_FILE | tr '\n' '\0' | xargs -0 ${SPARK_HOME}/bin/spark-submit
}

function do_cleanup() {
  rm $CA_CERT_FILE
  rm $CLIENT_CERT_FILE
  rm $CLIENT_KEY_FILE
  rm $PARAM_FILE
}