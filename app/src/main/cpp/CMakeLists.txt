cmake_minimum_required(VERSION 3.22.1)
project("termlab_native")

add_library(termlab_native SHARED native-lib.cpp)
find_library(log-lib log)
target_link_libraries(termlab_native ${log-lib})
