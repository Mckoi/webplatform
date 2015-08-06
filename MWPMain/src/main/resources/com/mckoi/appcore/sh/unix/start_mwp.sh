#!/bin/sh

# This command starts the Mckoi Web Platform service on this machine as
# defined in the parent sub-directory.

java -Djava.security.egd=file:/dev/./urandom -cp ${lib_directory}/*: com.mckoi.appcore.AppServiceNode ${root_directory}/mwp_main.conf
