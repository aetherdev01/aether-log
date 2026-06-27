cmake_minimum_required(VERSION 3.22.1)

project("loglog_ads")

# Library native ads_config
add_library(
    lv          # nama .so → libloglog_ads.so
    SHARED
    lv.cpp
)

# Link Android log library (opsional, untuk debugging native)
find_library(log-lib log)

target_link_libraries(
    lv
    ${log-lib}
)
