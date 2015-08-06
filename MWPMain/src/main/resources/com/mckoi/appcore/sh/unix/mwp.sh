#!/bin/sh

# Mckoi Web Platform administration shell commands.

java -Djava.security.egd=file:/dev/./urandom -cp ${lib_directory}/*: com.mckoi.appcore.AppCoreAdmin -netconfinfo ${root_directory}/netconf_info -client ${root_directory}/client.conf ${1} ${2} ${3} ${4} ${5} ${6} ${7} ${8} ${9}
