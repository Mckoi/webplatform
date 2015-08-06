#!/bin/sh

#Run the node install tool

java -Djava.security.egd=file:/dev/./urandom -cp support/*: com.mckoi.appcore.NodeInstallTool