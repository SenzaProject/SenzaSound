#!/usr/bin/env bash
# Mengambil header AOSP yang dibutuhkan (audio_effect.h dkk). Baris terakhir keluaran = daftar include (;).
set -euo pipefail
OUT="${1:-aosp-headers}"
TAG="${AOSP_TAG:-android-14.0.0_r1}"
mkdir -p "$OUT"
cd "$OUT"

sparse_clone() {  # nama url path...
  local name="$1" url="$2"; shift 2
  if [ ! -d "$name" ]; then
    git clone --quiet --depth 1 --filter=blob:none --sparse --branch "$TAG" "$url" "$name" 1>&2 \
      || git clone --quiet --depth 1 --filter=blob:none --sparse "$url" "$name" 1>&2
    git -C "$name" sparse-checkout set "$@" 1>&2
  fi
}

sparse_clone media https://android.googlesource.com/platform/system/media audio/include
sparse_clone libhardware https://android.googlesource.com/platform/hardware/libhardware include
sparse_clone core https://android.googlesource.com/platform/system/core libcutils/include libsystem/include liblog/include

D="$PWD"
echo "$D/media/audio/include;$D/libhardware/include;$D/core/libcutils/include;$D/core/libsystem/include;$D/core/liblog/include"
