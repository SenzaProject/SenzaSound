cmake_minimum_required(VERSION 3.18)
project(senzafx CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Daftar include AOSP (dipisah ;) dari scripts/fetch_headers.sh
set(AOSP_INCLUDES "" CACHE STRING "Direktori header AOSP")

add_library(senzafx SHARED src/senza_effect.cpp)
target_include_directories(senzafx PRIVATE src ${AOSP_INCLUDES})
target_compile_options(senzafx PRIVATE -Wall -Wextra -O2 -fno-exceptions -fno-rtti -fvisibility=hidden)
target_link_options(senzafx PRIVATE -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
