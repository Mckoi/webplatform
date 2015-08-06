#!/bin/sh

# Starts both the MckoiDDB and Mckoi Web Service 'daemon' service as
# defined by the installation.

java -Djava.security.egd=file:/dev/./urandom -cp ${lib_directory}/*: com.mckoi.appcore.RunAllServices ${root_directory}
