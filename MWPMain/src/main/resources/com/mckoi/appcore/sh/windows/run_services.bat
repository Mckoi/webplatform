@echo off

rem Starts both the MckoiDDB and Mckoi Web Service 'daemon' service as
rem defined by the installation.

java -cp ${lib_directory}\*; com.mckoi.appcore.RunAllServices ${root_directory}