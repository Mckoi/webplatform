#!/bin/sh

# Registers this server with the Mckoi Web Process database.
# This only needs to be run once per each installation after the
# web platform has been initialized.

java -Djava.security.egd=file:/dev/./urandom -cp ${lib_directory}/*: com.mckoi.appcore.MWPServerRegister ${root_directory}/mwp_main.conf