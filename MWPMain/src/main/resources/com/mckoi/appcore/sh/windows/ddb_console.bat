@echo off

rem This command starts the MckoiDDB machine node on this machine as
rem defined in the parent sub-directory.

java -cp ${lib_directory}\*; com.mckoi.runtime.AdminConsole -netconfinfo ${root_directory}\netconf_info -netpassword ${network_password} -if ${net_interface}