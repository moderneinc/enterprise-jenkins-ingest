#!/bin/bash

MVN_DIR=".mvn"
JVM_CONFIG="jvm.config"
MVN_MEM="Xmx2048m"
# Create the .mvn directory if it does not exist.
if [ ! -d "$MVN_DIR" ]; then
    mkdir "$MVN_DIR"
fi

# Create the jvm.config file if it does not exist.$
if [ ! -f "$MVN_DIR/$JVM_CONFIG" ]; then
  touch "$MVN_DIR/$JVM_CONFIG"
  echo "-$MVN_MEM" >> "$MVN_DIR/$JVM_CONFIG"
else
  RESULT="$(grep -E Xmx[0-9]+.* $MVN_DIR/$JVM_CONFIG)"
  if [ -z "$RESULT" ]; then
    echo " -$MVN_MEM" >> "$MVN_DIR/$JVM_CONFIG"
  else
    sed -i "s/Xmx[0-9]*[MmGg]/$MVN_MEM/" "$MVN_DIR/$JVM_CONFIG"
  fi
fi