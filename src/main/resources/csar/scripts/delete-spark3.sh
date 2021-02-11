#!/bin/bash

# set exit on error
set -e

# Remove files
rm -f "${PARAMETER_FILE}"
rm -f "${KUBECONFIG_FILE}"
rm -f "${CONFIG_FILE}"
rm -f "${DRIVER_POD_TEMPLATE_FILE}"
rm -f "${EXECUTOR_POD_TEMPLATE_FILE}"


