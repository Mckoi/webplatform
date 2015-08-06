@echo off

rem Registers this server with the Mckoi Web Process database.
rem This only needs to be run once per each installation after the
rem web platform has been initialized.

java -cp ${lib_directory}\*; com.mckoi.appcore.MWPServerRegister ${root_directory}\mwp_main.conf