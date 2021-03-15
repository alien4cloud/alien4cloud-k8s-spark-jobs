#!/bin/bash

getPodNameCmd="kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} get po -l job_id=${TOSCA_JOB_ID} --no-headers -o custom-columns=:.metadata.name"
if [ "$debug_operations" == "true" ]; then
  echo "Get pod name command : $getPodNameCmd"
fi

POD_NAME=$(eval $getPodNameCmd)
if [ "$debug_operations" == "true" ]; then
  echo "pod name is : $POD_NAME"
fi

cleanPodCmd="kubectl --kubeconfig ${KUBECONFIG_FILE} -n ${NAMESPACE} delete po ${POD_NAME}"
if [ "$debug_operations" == "true" ]; then
  echo "Clean pod command : $cleanPodCmd"
fi
eval "$cleanPodCmd"
