#!/bin/bash

export TOSCA_JOB_ID="$(uuidgen)"

CA_CERT_FILE=$(mktemp)
CLIENT_CERT_FILE=$(mktemp)
CLIENT_KEY_FILE=$(mktemp)

echo $CA_CERT | base64 -d > $CA_CERT_FILE
echo $CLIENT_CERT | base64 -d > $CLIENT_CERT_FILE
echo $CLIENT_KEY | base64 -d > $CLIENT_KEY_FILE

ANNOTATIONS_OPTS=$(echo ${ANNOTATIONS} | jq -r 'to_entries | map("--conf spark.kubernetes.driver.annotation.\(.key)=\(.value) --conf spark.kubernetes.executor.annotation.\(.key)=\(.value)") | join(" ")')

echo "Submitting JOB_ID: ${TOSCA_JOB_ID}"

$SPARK_HOME/bin/spark-submit \
  --master k8s://$SERVER \
  --deploy-mode cluster \
  --name ${NODE} \
  --class ${JAVA_CLASS}\
  --conf spark.kubernetes.authenticate.submission.caCertFile=$CA_CERT_FILE \
  --conf spark.kubernetes.authenticate.submission.clientKeyFile=$CLIENT_KEY_FILE \
  --conf spark.kubernetes.authenticate.submission.clientCertFile=$CLIENT_CERT_FILE \
  --conf spark.kubernetes.container.image=${CONTAINER_NAME} \
  --conf spark.kubernetes.namespace=${NAMESPACE} \
  --conf spark.executor.instances=${BATCH_SIZE} \
  ${ANNOTATIONS_OPTS} \
  --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark-sa \
  --conf spark.kubernetes.driver.label.job_id=${TOSCA_JOB_ID} \
  --conf spark.kubernetes.submission.waitAppCompletion=false \
  ${JAR_FILE}

rm $CA_CERT_FILE
rm $CLIENT_CERT_FILE
rm $CLIENT_KEY_FILE
