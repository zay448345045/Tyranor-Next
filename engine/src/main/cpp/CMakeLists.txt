cmake_minimum_required(VERSION 3.22.1)
project(tyranor_krkr_bridge)

add_library(krkr_bridge_v2 SHARED krkr_bridge.cpp)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(krkr_bridge_v2 ${log-lib} ${dl-lib})
