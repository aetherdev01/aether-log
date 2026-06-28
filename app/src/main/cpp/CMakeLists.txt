cmake_minimum_required(VERSION 3.22.1)

project("loglog_native")

# ── lv.so — ads config (XOR-obfuscated Unity Ads IDs) ────────────────────────
add_library(lv SHARED lv.cpp)

find_library(log-lib log)

target_link_libraries(lv ${log-lib})

# ── xplus.so — fast log search + license key validator ───────────────────────
add_library(xplus SHARED "X+.cpp")

target_compile_options(xplus PRIVATE
    -O2          # optimasi kecepatan
    -fvisibility=hidden   # sembunyikan simbol internal dari .so
)

target_link_libraries(xplus ${log-lib})
