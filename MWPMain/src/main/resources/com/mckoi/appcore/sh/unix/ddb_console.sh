#!/bin/sh

# The Mckoi DDB console.

java -Djava.security.egd=file:/dev/./urandom -cp ${lib_directory}/MckoiDDB*.jar com.mckoi.runtime.AdminConsole -netconfinfo ${root_directory}/netconf_info -netpassword ${network_password} -if ${net_interface}
