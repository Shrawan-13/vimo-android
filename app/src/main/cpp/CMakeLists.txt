cmake_minimum_required(VERSION 3.22.1)
project(vimo_native)

# The Oboe shim.
#
# Oboe comes from Maven as a prefab dependency, so `find_package(oboe)` finds
# the headers and the library through the prefab configuration that the
# Gradle build generates.
add_library(vimo_oboe SHARED vimo_oboe.cpp)

find_package(oboe REQUIRED CONFIG)
target_link_libraries(vimo_oboe oboe::oboe log)

# The Rust libraries link against this one, so it has to land next to them.
set_target_properties(vimo_oboe PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY
        ${CMAKE_SOURCE_DIR}/../../../jniLibs/${ANDROID_ABI})
target_compile_options(vimo_oboe PRIVATE -O2 -fvisibility=hidden)
