#!/bin/sh

# This batch file runs the MckoiDDB viewer tool for the Mckoi network
# specified by the client.conf file.

java -Djava.security.egd=file:/dev/./urandom -cp ${lib_directory}/MckoiDDB*.jar com.mckoi.runtime.DBViewer ${root_directory}/client.conf
