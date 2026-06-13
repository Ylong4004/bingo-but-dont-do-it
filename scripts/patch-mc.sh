#!/bin/bash
set -e

# Usage: bash scripts/patch-mc.sh mc1.21.3/src/main/java/me/jfenn/bingo/impl/EntityManagerImpl.kt
# $1 = input file

TMPDIR=/tmp/patch-mc.$$

mkdir -p $TMPDIR

git diff -p $1 > $TMPDIR/file.patch

for impl in ./mc*; do
  implFile="$impl/${1#*/}"

  # If the file is the input, skip patching
  if [[ $implFile -ef $1 ]]; then continue; fi
  echo "$implFile"

  # If the file/dir doesn't exist, create it
  implDir=$(dirname $implFile)
  if [ ! -f $implDir ]; then mkdir -p $implDir; fi
  if [ ! -f $implFile ]; then
    cp "$1" "$implFile"
    continue
  fi

  # Apply the patch
  patch --posix --no-backup-if-mismatch --merge -r - -f $implFile $TMPDIR/file.patch || true
done

rm -rf $TMPDIR
