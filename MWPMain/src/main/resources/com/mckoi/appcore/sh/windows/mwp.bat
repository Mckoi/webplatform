@echo off

rem Mckoi Web Platform administration shell commands.

java -cp ${lib_directory}\*; com.mckoi.appcore.AppCoreAdmin -netconfinfo ${root_directory}\netconf_info -client ${root_directory}\client.conf %1 %2 %3 %4 %5 %6 %7 %8 %9 
