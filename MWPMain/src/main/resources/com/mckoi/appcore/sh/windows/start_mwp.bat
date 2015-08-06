@echo off

rem This command starts the Mckoi Web Platform service on this machine as
rem defined in the parent sub-directory.

java -cp ${lib_directory}\*; com.mckoi.appcore.AppServiceNode ${root_directory}\mwp_main.conf
