#!/bin/bash

K8S_STATUS=$(kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} get po -l job_id=${TOSCA_JOB_ID} --no-headers -o custom-columns=:.status.phase)

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

if [ "$TOSCA_JOB_STATUS" != "RUNNING" ]; then
  POD_NAME=$(kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} get po -l job_id=${TOSCA_JOB_ID} --no-headers -o custom-columns=:.metadata.name)
  kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} logs ${POD_NAME}

  # Clean the pod
  kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} delete po ${POD_NAME}
fi

#rm $KUBECONFIG_FILE
