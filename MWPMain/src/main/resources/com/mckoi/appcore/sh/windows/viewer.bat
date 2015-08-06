@echo off

rem This batch file runs the MckoiDDB viewer tool for the Mckoi network
rem specified by the client.conf file.

java -cp ${lib_directory}\*; com.mckoi.runtime.DBViewer ${root_directory}\client.conf