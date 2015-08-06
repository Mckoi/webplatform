#!/bin/sh

# This command starts the MckoiDDB machine node on this machine as
# defined in the parent sub-directory.

java -Djava.security.egd=file:/dev/./urandom -cp ${lib_directory}/MckoiDDB*.jar com.mckoi.runtime.MckoiMachineNode -nodeconfig ${root_directory}/node.conf -netconfinfo ${root_directory}/netconf_info -host ${this_ddb_address} -port ${this_ddb_port}
