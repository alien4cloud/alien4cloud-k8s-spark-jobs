#!/bin/bash -e

source $common

pre_submit

# Jar file
envsubst >> $PARAM_FILE <<EOF
--class
$JAVA_CLASS
$JAR_FILE
EOF

do_submit

do_cleanup
