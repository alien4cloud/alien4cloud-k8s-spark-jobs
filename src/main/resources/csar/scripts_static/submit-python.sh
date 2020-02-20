#!/bin/bash

source $common

#pre_submit

# Jar file
envsubst >> $PARAM_FILE <<EOF
$PY_FILE
EOF

do_submit

do_cleanup
