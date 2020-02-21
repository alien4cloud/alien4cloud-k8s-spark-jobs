#!/bin/bash -e

source $common

pre_submit

# Jar file
#envsubst >> $PARAM_FILE <<EOF
#$PY_FILE
#EOF

if [ ! -z "$PYTHON_VERSION" ]; then
envsubst >> $PARAM_FILE <<EOF
--conf
spark.kubernetes.pyspark.pythonVersion="${PYTHON_VERSION}"
EOF
fi

if [ -f $PYTHON_CONFIG_ARGS_FILE_PATH ]; then
  if [ "$debug_operations" == "true" ]; then
    echo "PYTHON_CONFIG_ARGS_FILE_PATH is $PYTHON_CONFIG_ARGS_FILE_PATH"
    cat $PYTHON_CONFIG_ARGS_FILE_PATH
  fi
  cat $PYTHON_CONFIG_ARGS_FILE_PATH >> $PARAM_FILE
fi

do_submit

do_cleanup
