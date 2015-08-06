@echo off

rem This command starts the MckoiDDB machine node on this machine as
rem defined in the parent sub-directory.

java -cp ${lib_directory}\*; com.mckoi.runtime.MckoiMachineNode -nodeconfig ${root_directory}\node.conf -netconfinfo ${root_directory}\netconf_info -host ${this_ddb_address} -port ${this_ddb_port}