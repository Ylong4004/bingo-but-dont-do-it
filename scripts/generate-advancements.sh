#!/bin/bash

SCRIPT_DIR=$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")
TMP_DIR=$(realpath $SCRIPT_DIR/tmp)
DATA_DIR=$(realpath $SCRIPT_DIR/data)
DOCS_DIR=$(realpath $SCRIPT_DIR/../docs)

pushd $TMP_DIR

# Download the Minecraft Client jar
source $SCRIPT_DIR/env.sh
[ -f client.jar ] || wget $CLIENT_JAR

# Extract the data folder
jar -xf client.jar data

ADVANCEMENTS="{}"

# Create a JSON list of all advancements
for file in $(find data/minecraft/advancement/ -name '*.json'); do
  if ! jq -e 'has("display")' $file >/dev/null; then
    continue
  fi
  fileParts=${file//data\/minecraft\/advancement\//}
  advancement="minecraft:${fileParts//.json/}"

  ADVANCEMENTS=$(echo $ADVANCEMENTS | jq ".[\"$advancement\"] = $(jq '.display.icon.id' $file)")
done

echo $ADVANCEMENTS | jq > $DATA_DIR/advancements.json

popd
