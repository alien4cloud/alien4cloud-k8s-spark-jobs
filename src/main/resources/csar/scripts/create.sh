#!/bin/bash
if [ ! -d $HOME/tmp ]; then
 mkdir -p $HOME/tmp
fi

export PARAMETER_FILE=$(mktemp -p $HOME/tmp)
