#!/bin/bash

source $common

# Jar file
envsubst >> $PARAM_FILE <<EOF
$PY_FILE
EOF

do_submit

do_cleanup
