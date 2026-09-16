include_guard(GLOBAL)

# Reads a source list from one of OrcaSlicer's own CMakeLists.txt files, so
# files added or removed upstream reach the Android build without manual edits.
#
#   orca_read_cmake_list(<cmake-file> <regex-of-opening> <out-var>)
#
# <regex-of-opening> matches the text before the list, for example
# "set\\(lisbslic3r_sources". Comments are ignored.
function(orca_read_cmake_list cmake_file opening out_var)
    file(READ "${cmake_file}" content)
    string(REGEX MATCH "${opening}[^)]*\\)" block "${content}")
    if(NOT block)
        message(FATAL_ERROR "Cannot find '${opening}' in ${cmake_file}; review the Android engine build")
    endif()
    string(REGEX REPLACE "^${opening}" "" block "${block}")
    string(REGEX REPLACE "\\)$" "" block "${block}")
    string(REPLACE ";" "\\;" block "${block}")
    string(REPLACE "\n" ";" lines "${block}")

    set(entries "")
    foreach(line IN LISTS lines)
        string(REGEX REPLACE "#.*$" "" line "${line}")
        string(STRIP "${line}" line)
        if(line)
            separate_arguments(words UNIX_COMMAND "${line}")
            list(APPEND entries ${words})
        endif()
    endforeach()
    set(${out_var} "${entries}" PARENT_SCOPE)
endfunction()

# Removes Android exclusions from an upstream list. Every exclusion must still
# exist upstream; otherwise the build stops so the exclusion is reviewed.
function(orca_exclude_sources list_var)
    set(sources "${${list_var}}")
    foreach(excluded IN LISTS ARGN)
        list(FIND sources "${excluded}" index)
        if(index EQUAL -1)
            message(FATAL_ERROR "Excluded source '${excluded}' no longer exists upstream; update the Android exclusion list")
        endif()
        list(REMOVE_ITEM sources "${excluded}")
    endforeach()
    set(${list_var} "${sources}" PARENT_SCOPE)
endfunction()
