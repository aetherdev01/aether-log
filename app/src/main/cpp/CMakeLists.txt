cmake_minimum_required(VERSION 3.22.1)

project("loglog_ads")

# Library native ads_config
add_library(
    loglog_ads          # nama .so → libloglog_ads.so
    SHARED
    ads_config.cpp
)

# Link Android log library (opsional, untuk debugging native)
find_library(log-lib log)

target_link_libraries(
    loglog_ads
    ${log-lib}
)
