#!/bin/bash -e

statusCmd="kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} get po -l job_id=${TOSCA_JOB_ID} --no-headers -o custom-columns=:.status.phase"
if [ "$debug_operations" == "true" ]; then
  echo "status command : ${statusCmd}"
fi

K8S_STATUS=$(echo $statusCmd | bash)
echo "K8S_STATUS: $K8S_STATUS"

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
  getPodNameCmd="kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} get po -l job_id=${TOSCA_JOB_ID} --no-headers -o custom-columns=:.metadata.name"
  if [ "$debug_operations" == "true" ]; then
    echo "Get pod name command : $getPodNameCmd"
  fi

  POD_NAME=$(eval $getPodNameCmd)
  if [ "$debug_operations" == "true" ]; then
    echo "pod name is : $POD_NAME"
  fi

  getLogCmd="kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} logs ${POD_NAME}"
  if [ "$debug_operations" == "true" ]; then
    echo "Get log command : $getLogCmd"
  fi
  eval "$getLogCmd"

  # Clean the pod
  cleanPodCmd="kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} delete po ${POD_NAME}"
  if [ "$debug_operations" == "true" ]; then
    echo "Clean pod command : $cleanPodCmd"
  fi
  eval "$cleanPodCmd"

fi

