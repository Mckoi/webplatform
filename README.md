## MWP: Mckoi Web Platform

At its core, the Mckoi Web Platform is a web application server and
distributed process network that operates over a MckoiDDB network. The web
server used is an embedded version of Jetty 8. Command line tools are
provided to set up installations of MWP on servers, and to administer
deployed installations.

The Mckoi Web Platform operates entirely within the context of a MckoiDDB
data network. This means that when a web application is deployed the
application files (the .war) must be copied into a file system running over
MckoiDDB because the file system of the Jetty server's host is not
accessible. Web applications must use MckoiDDB for all persistent storage of
data.

This software also includes an HTML5/JavaScript console application that is
deployed to a Mckoi Web Platform installation, similar to a CLI you would
find in an operating system. The CLI is used as a web application
development platform and for administration of the network. The CLI provides
various applications including a text editor, log viewer, database viewer,
file system commands (such as 'ls', 'mv', 'rm', 'more', etc) and supports a
modified version of the Node (https://nodejs.org/) JavaScript API.
