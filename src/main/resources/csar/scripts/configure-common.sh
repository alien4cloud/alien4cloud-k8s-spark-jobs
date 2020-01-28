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

function common_configure_cleanup {
  rm $KUBECONFIG_FILE
}

trap common_configure_cleanup EXIT
