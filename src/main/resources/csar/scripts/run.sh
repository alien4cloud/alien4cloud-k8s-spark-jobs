#!/bin/bash

KUBECONFIG_FILE=$(mktemp)
envsubst > ${KUBECONFIG_FILE} <<EOF
apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: ${CA_CERT}
    server: ${SERVER}
  name: kubernetes
contexts:
- context:
    cluster: kubernetes
    user: kubernetes-admin
  name: kubernetes-admin@kubernetes
current-context: kubernetes-admin@kubernetes
kind: Config
preferences: {}
users:
- name: kubernetes-admin
  user:
    client-certificate-data: ${CLIENT_CERT}
    client-key-data: ${CLIENT_KEY}
EOF

K8S_STATUS=$(kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} get po -l job_id=${TOSCA_JOB_ID} --no-headers -o custom-columns=:.status.phase)

rm $KUBECONFIG_FILE

# Default status in RUNNING
export TOSCA_JOB_STATUS="RUNNING"

if [ -z "$K8S_STATUS" ]; then
  # Status unknown , the pod no longer exists (someone deleted it).
  # Job marked completed
  echo "Job pod no longer exists."
  export TOSCA_JOB_STATUS="COMPLETED"
elif [ "$K8S_STATUS" = "Succeeded" ]; then
  echo "Job done."
  export TOSCA_JOB_STATUS="COMPLETED"
elif [ "$K8S_STATUS" = "Failed" ]; then
  echo "Job Failed."
  export TOSCA_JOB_STATUS="FAILED"
fi

