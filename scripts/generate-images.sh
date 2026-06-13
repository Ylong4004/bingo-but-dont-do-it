#!/bin/bash
set -e

ANSI_RED='\033[0;31m'
ANSI_YELLOW='\033[0;33m'
ANSI_RESET='\033[0m'

SCRIPT_DIR=$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")
TMP_DIR=$SCRIPT_DIR/tmp

mkdir -p $TMP_DIR
pushd $TMP_DIR

source $SCRIPT_DIR/env.sh
[ -f client.jar ] || wget $CLIENT_JAR
JAR_FILE="$(realpath client.jar)"

[ -f $JAR_FILE ] || {
  echo -e "${ANSI_RED}Error: minecraft jar for $VERSION not found."
  exit 1
}

ITEMS_FILE="$(realpath $SCRIPT_DIR/data/items.json)"
[ -f $ITEMS_FILE ] || {
  echo -e "${ANSI_RED}Error: items.json missing. Run scripts/generate-reports.sh first!"
  exit 1
}

IMAGE_MASK_DIR="$(realpath $SCRIPT_DIR/data/masks)"
IMAGE_OVERRIDE_DIR="$(realpath $SCRIPT_DIR/data/images)"

OUT_DIR="$(realpath $SCRIPT_DIR/../common/src/main/resources/yet-another-minecraft-bingo/images)"
mkdir -p $OUT_DIR

# get a file path to an image from the provided texture id
# $1 = texture ("minecraft:block/oak_planks")
function get_texture_file {
  texture_id=$(echo $1 | xargs | rev | cut -d ':' -f1 | rev)
  texture_file=$(realpath ./assets/minecraft/textures/$texture_id.png)

  [ -f $texture_file ] || {
    echo -e "${ANSI_RED}Error: texture file for $texture_file does not exist!"
    exit 1
  }

  echo -n $texture_file
}

# Copy a texture identifier directly to the output dir
# $1 = input texture file (.png)
# $2 = output file (.png)
function copy_texture {
  # copy the texture to the out_dir
  convert $1 -background none -extent 16x16 PNG32:$2
}

# Apply a mask image to the texture identifier before writing to the output dir
# $1 = input texture file (.png)
# $2 = mask image file (/home/.../data/masks/image.png)
# $3 = output file (.png)
function mask_texture {
  # mask the image with ImageMagick, and write to the output dir
  # https://stackoverflow.com/a/22417663
  # convert $1 \( $2 \) -compose CopyOpacity -composite PNG32:$3
  convert $1 \( $2 \) -compose Multiply -composite \( $2 \) -compose CopyOpacity -composite PNG32:$3
  # convert $1 -alpha set -background none -extent 16x16 \( $2 -alpha copy -background none \) -channel rgba -compose multiply -composite PNG32:$3
}

function multiply_texture {
  # mask the image with ImageMagick, and write to the output dir
  # https://stackoverflow.com/a/22417663
  # convert $1 \( $2 \) -compose CopyOpacity -composite PNG32:$3
  convert $1 \( $2 \) -compose CopyOpacity -composite \( $2 \) -compose Multiply -composite PNG32:$3
}

# Apply a color tint to the texture identifier before writing it to the output dir
# $1 = input texture file (.png)
# $2 = color (#ffffff)
# $3 = output file (.png)
function color_texture {
  # tint the image with ImageMagick, and write to the output dir
  # https://www.imagemagick.org/discourse-server/viewtopic.php?t=33380
  # convert $texture_file -colorspace gray -fill $2 -tint 100 PNG32:$OUT_DIR/$(echo $3 | xargs).png
  # convert $1 -background none \( xc:$(echo $2 | xargs) -scale 16x16 -background none \) -compose multiply -channel rgb -composite PNG32:$3
  # convert $1 -colorspace gray -fill $2 -tint 100 PNG32:$3
  #convert $1 \( xc:$(echo $2 | xargs) -scale 16x16 \) -compose multiply -channel rgb -composite PNG32:$3
  convert xc:$(echo $2 | xargs) -scale 16x16 \( $1 \) -compose CopyOpacity -composite \( $1 \) -compose Multiply -composite PNG32:$3
}

# extract jar assets (into ./assets)
jar -xf $JAR_FILE assets

# loop over items registry
for item in $(jq -r ".[]" $ITEMS_FILE); do
  item_id="$(echo $item | cut -d ':' -f2)"
  echo -ne "${ANSI_RESET}${item_id}: "

  item_out_file=$OUT_DIR/$(echo $item_id | xargs).png

  # if there's an image override, copy that instead of proceeding further
  if [ -f $IMAGE_OVERRIDE_DIR/$(echo $item_id | xargs).png ]; then
    echo "OVERRIDE"
    copy_texture $IMAGE_OVERRIDE_DIR/$(echo $item_id | xargs).png $item_out_file
    continue
  fi

  item_file=./assets/minecraft/items/"${item_id}.json"

  function resolve_model() {
    _type=$(echo $1 | jq -r .type)
    if [ "$_type" == "minecraft:model" ]; then
      echo $1 | jq -r .model
    elif [ "$_type" == "minecraft:select" ]; then
      resolve_model "$(echo $1 | jq -c .cases[0].model)"
    elif [ "$_type" == "minecraft:special" ]; then
      echo $1 | jq -r .base
    elif [ "$_type" == "minecraft:condition" ]; then
      resolve_model "$(echo $1 | jq -c .on_false)"
    elif [ "$_type" == "minecraft:range_dispatch" ]; then
      resolve_model "$(echo $1 | jq -c .entries[0].model)"
    else
      >&2 echo "Unknown type: $_type in $item_file"
      exit 1
    fi
  }

  item_model_id=$(resolve_model "$(jq -c .model $item_file)")
  item_model_file=./assets/minecraft/models${item_model_id/minecraft://}.json

  [ -f $item_model_file ] || {
    echo -e "${ANSI_YELLOW}Warning: item model for $item_id not found"
    continue
  }

  # temporarily copy the new item model to tmp.json
  cp -f $item_model_file tmp.json

  # collect an array of all item models encountered in the hierarchy
  item_models="[\"$item_model_id\"]"

  # resolve all model parents, up to a generated model
  while item_parent="$(jq -r '.parent' tmp.json)" && [ "$item_parent" != "null" ] && [ "$item_parent" != "block/block" ] && [ "$item_parent" != "builtin/generated" ] && [ "$item_parent" != "builtin/entity" ]; do
    # locate the next item parent file
    item_parent_id=$(echo $item_parent | cut -d ':' -f2)
    item_parent_file=./assets/minecraft/models/${item_parent_id}.json
    [ -f $item_parent_file ] || {
      echo -e "${ANSI_YELLOW}Warning: parent model for $item_parent not found"
      exit 1
    }

    # add the item parent to the item_models array
    item_models=$(echo $item_models | jq ". + [\"$item_parent\"]")

    # merge the model with its parent
    item_model_json="$(jq -s '.[0] * .[1]' tmp.json $item_parent_file)" || {
      echo -e "${ANSI_YELLOW}Error: could not resolve model parent $item_parent_id from $item_id"
      exit 1
    }

    # ensure that .parent is set from the parent file
    item_model_json=$(echo $item_model_json | jq ".parent = $(jq .parent $item_parent_file)")

    # overwrite tmp.json
    echo "$item_model_json" > tmp.json
  done

  # add the last item_parent to item_models
  item_models=$(echo $item_models | jq ". + [\"$item_parent\"]")

  # if the item is a block
  if [ "$item_parent" == "block/block" ]; then
    # try to find its side texture
    # ("beacon" for beacon blocks) ("pattern" for glazed terracotta) ("wool" for carpet models) ("wall" for wall models) (prefer "side" and "west" before top textures)
    block_texture=$(jq ".textures | (.end_rod // .beacon // .pattern // .wool // .wall // .texture // .all // .front // .side // .west // .top // .up // .flower)" tmp.json)

    [ "$block_texture" == "null" ] && {
      echo -e "${ANSI_RED}Error: block texture is null"
      echo $item_models
      cat tmp.json
      exit 1
    }

    block_texture_file=$(get_texture_file $block_texture)

    # see if any image masks are included in the item models
    image_mask_id="null"
    image_mask_file="null"
    for image_mask in $IMAGE_MASK_DIR/*; do
      image_mask_id="$(basename $image_mask | cut -d '.' -f1 | sed 's/__/:/g' | sed 's/\-/\//g')"
      if echo $item_models | jq -e ". | index( \"$image_mask_id\" )" >/dev/null; then
        image_mask_file=$image_mask
        break
      fi
    done

    # hack for bamboo fence & fence gate; these have a messy texture map that doesn't work well with the image masks
    if [ "$item_id" == "bamboo_fence" ] || [ "$item_id" == "bamboo_fence_gate" ]; then
      echo "USING MASK $image_mask_id: minecraft:block/bamboo_planks"
      mask_texture $(get_texture_file "minecraft:block/bamboo_planks") $image_mask_file $item_out_file
      continue
    fi

    # if it's stained glass, we need to apply a manual image mask
    if [[ "$item_id" == *"_stained_glass" ]]; then
      echo "STAINED GLASS"
      multiply_texture $block_texture_file $IMAGE_MASK_DIR/template_stained_glass.png $item_out_file
      continue
    fi

    # if the block is waxed, apply a waxed overlay
    if [[ "$item_id" == "waxed_"* ]]; then
      echo "WAXED"

      # if there is an image mask, write the masked texture to tmp.png
      if [ "$image_mask_file" != "null" ]; then
        mask_texture $block_texture_file $image_mask_file tmp.png
      else
        copy_texture $block_texture_file tmp.png
      fi

      # apply the wax overlay to tmp.png
      convert tmp.png $IMAGE_MASK_DIR/overlay_waxed.png -composite $item_out_file
      continue
    fi

    # if there is an image mask, write the masked texture
    [ "$image_mask_file" != "null" ] && {
      echo "USING MASK $image_mask_id: $block_texture"
      mask_texture $block_texture_file $image_mask_file $item_out_file
      continue
    }

    # if it's a leaf block, we need to apply the colormap: #48b518.
    if (echo $item_models | jq -e '. | index( "minecraft:block/leaves" )' >/dev/null) && [[ "$item_id" != "cherry"* ]]; then
      echo "USING COLOR TINT: $block_texture"
      color_texture $block_texture_file "#48b518" $item_out_file
      continue
    fi

    # otherwise, copy the full block texture
    echo -n "$block_texture :"
    echo $item_models
    copy_texture $block_texture_file $item_out_file
    continue
  fi

  # if the item is an item
  if [ "$item_parent" == "builtin/generated" ]; then
    item_texture=$(jq ".textures.layer0" tmp.json)

    [ "$item_texture" == "null" ] && {
      echo -e "${ANSI_RED}Error: item texture is null"
      echo $item_models
      cat tmp.json
      exit 1
    }

    item_texture_file=$(get_texture_file $item_texture)

    # if it's a grass/fern block, we need to apply the colormap: #48b518.
    if [ "$item_id" == "grass" ] || [ "$item_id" == "short_grass" ] || [ "$item_id" == "tall_grass" ] || [ "$item_id" == "fern" ] || [ "$item_id" == "large_fern" ] || [ "$item_id" == "vine" ] || [ "$item_id" == "lily_pad" ] || [ "$item_id" == "bush" ]; then
      # some items (leaves / grass / ferns) have color masks that need to be applied... #48b518.
      echo "USING COLOR TINT: $item_texture"
      color_texture $item_texture_file "#48b518" $item_out_file
      continue
    fi

    # if it's a glass pane, we need to apply a manual image mask
    if [[ "$item_id" == *"_glass_pane" ]]; then
      echo "GLASS PANE"
      multiply_texture $item_texture_file $IMAGE_MASK_DIR/template_glass_pane.png $item_out_file
      continue
    fi

    # if the item is leather or a potion, it needs to be tinted (and use both layers, if a second is provided)
    # matches: leather_horse_armor, leather_boots, ...
    if [[ "$item_id" == "leather_"* ]] || [[ "$item_id" == *"potion" ]] || [ "$item_id" == "tipped_arrow" ]; then
      item_color="gray"
      [[ "$item_id" == "leather_"* ]] && item_color="#945b3e"
      [[ "$item_id" == *"potion" ]] && item_color="#b743cc"
      [ "$item_id" == "tipped_arrow" ] && item_color="#ff0000"

      item_texture1=$(jq ".textures.layer1" tmp.json)
      if [ "$item_texture1" != "null" ]; then
        echo "TINTED ITEM WITH OVERLAY"
        # color the item texture and write to tmp.png
        color_texture $item_texture_file $item_color tmp.png
        item_texture1_file=$(get_texture_file $item_texture1)
        convert tmp.png $item_texture1_file -composite $item_out_file
      else
        echo "TINTED ITEM"
        # color the item texture and write to tmp.png
        color_texture $item_texture_file $item_color $item_out_file
      fi

      continue
    fi

    # if the item is waxed, apply a waxed overlay
    if [[ "$item_id" == "waxed_"* ]]; then
      echo "WAXED ITEM"

      # apply the wax overlay to tmp.png
      convert $item_texture_file $IMAGE_MASK_DIR/overlay_waxed.png -composite $item_out_file
      continue
    fi

    # copy the texture with no modifications
    echo $item_texture
    copy_texture $item_texture_file $item_out_file
    continue
  fi

  # if the item is a banner
  if echo $item_models | jq -e '. | index( "minecraft:item/template_banner" )' >/dev/null; then
    item_color=${item_id%_banner}
    # replace some unsupported item colors with ImageMagick ids
    [ "$item_color" == "black" ] && item_color="#000000"
    [ "$item_color" == "blue" ] && item_color="#253193"
    [ "$item_color" == "brown" ] && item_color="#56331c"
    [ "$item_color" == "cyan" ] && item_color="#17a2a2"
    [ "$item_color" == "gray" ] && item_color="#414141"
    [ "$item_color" == "green" ] && item_color="#597a28"
    [ "$item_color" == "lime" ] && item_color="#39ba2e"
    [ "$item_color" == "light_blue" ] && item_color="#6387d2"
    [ "$item_color" == "light_gray" ] && item_color="#a0a7a7"
    [ "$item_color" == "magenta" ] && item_color="#be49c9"
    [ "$item_color" == "pink" ] && item_color="#e890b0"
    [ "$item_color" == "purple" ] && item_color="#7e34bf"
    [ "$item_color" == "red" ] && item_color="#9e2b27"
    [ "$item_color" == "white" ] && item_color="#e4e4e4"
    [ "$item_color" == "yellow" ] && item_color="#faee4d"

    echo "BANNER TINT: $item_color"
    # convert $IMAGE_MASK_DIR/template_banner.png -colorspace gray -fill $item_color -tint 300 PNG32:$OUT_DIR/$(echo $item_id | xargs).png
    convert $IMAGE_MASK_DIR/template_banner.png \( +clone -colorspace gray -function polynomial -4,4,0 -background $item_color -alpha shape \) -channel rgb -composite PNG32:$OUT_DIR/$(echo $item_id | xargs).png
    continue
  fi

  # if the item is a bed
  if echo $item_models | jq -e '. | index( "minecraft:item/template_bed" )' >/dev/null; then
    item_color=${item_id%_bed}
    # replace some unsupported item colors with ImageMagick ids
    [ "$item_color" == "blue" ] && item_color="#253193"
    [ "$item_color" == "brown" ] && item_color="#56331c"
    [ "$item_color" == "cyan" ] && item_color="#17a2a2"
    [ "$item_color" == "gray" ] && item_color="#414141"
    [ "$item_color" == "green" ] && item_color="#597a28"
    [ "$item_color" == "lime" ] && item_color="#39ba2e"
    [ "$item_color" == "light_blue" ] && item_color="#6387d2"
    [ "$item_color" == "light_gray" ] && item_color="#a0a7a7"
    [ "$item_color" == "magenta" ] && item_color="#be49c9"
    [ "$item_color" == "pink" ] && item_color="#e890b0"
    [ "$item_color" == "purple" ] && item_color="#7e34bf"
    [ "$item_color" == "red" ] && item_color="#9e2b27"
    [ "$item_color" == "white" ] && item_color="#e4e4e4"
    [ "$item_color" == "yellow" ] && item_color="#faee4d"

    echo "BED TINT: $item_color"
    # convert $IMAGE_MASK_DIR/template_bed.png -colorspace gray -fill $item_color -tint 300 PNG32:$OUT_DIR/$(echo $item_id | xargs).png
    # convert $IMAGE_MASK_DIR/template_bed.png \( xc:$item_color -scale 16x16 \) -compose multiply -channel rgb -composite PNG32:$OUT_DIR/$(echo $item_id | xargs).png
    convert $IMAGE_MASK_DIR/template_bed.png \( +clone -colorspace gray -function polynomial -4,4,0 -background $item_color -alpha shape \) -channel rgb -composite PNG32:$OUT_DIR/$(echo $item_id | xargs).png
    continue
  fi

  # otherwise, try to find an image file that matches...
  fallback_image=./assets/minecraft/textures/item/$item_id.png
  [ -f $fallback_image ] && {
    echo "FALLBACK ITEM"
    cp -f $fallback_image $OUT_DIR/$(echo $item_id | xargs).png
    continue
  }

  fallback_image=./assets/minecraft/textures/block/$item_id.png
  [ -f $fallback_image ] && {
    echo "FALLBACK BLOCK"
    cp -f $fallback_image $OUT_DIR/$(echo $item_id | xargs).png
    continue
  }

  if [ "$item_parent" == "null" ]; then
    echo AIR
    continue
  fi

  echo -e "${ANSI_RED}Error: NO IMAGE FOUND"
  # TODO:
  # - player_head
  # - piglin_head
  # - zombified_piglin_head
done

touch -d '2000-01-01 01:01:01 UTC' $OUT_DIR/*.png
zip -j -9 $(realpath $SCRIPT_DIR/../common/images.zip) $OUT_DIR/*.png

popd
