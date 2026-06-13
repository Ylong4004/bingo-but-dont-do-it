#!/bin/bash
set -e

SCRIPT_DIR=$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")
TMP_DIR=$(realpath $SCRIPT_DIR/tmp)
DATA_DIR=$(realpath $SCRIPT_DIR/data)
DOCS_DIR=$(realpath $SCRIPT_DIR/../docs)

mkdir -p $TMP_DIR
pushd $TMP_DIR

# Download the Minecraft Server jar
source $SCRIPT_DIR/env.sh
[ -f server.jar ] || wget $SERVER_JAR

# Generate report files (or --all)
java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports

# Copy the items list into data/items.json
mkdir -p $DATA_DIR
cat generated/reports/registries.json | jq '.["minecraft:item"].entries | keys' > $DATA_DIR/items.json

# Copy the items list to the docs site
mkdir -p $DOCS_DIR/data
cp $DATA_DIR/items.json $DOCS_DIR/data/items.json

popd
