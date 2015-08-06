## MWP: Mckoi Web Platform

At its core, the Mckoi Web Platform is a web application server and
distributed process network the operates over a MckoiDDB network. The web
server is an embedded version of Jetty. Command line tools are provided to
set up installations of MWP on servers, and to administer deployed
installations.

The Mckoi Web Platform operates entirely within the context of a MckoiDDB
data network. This means, when a web application is deployed the application
files must be copied into a file system running over MckoiDDB.

This software also includes a web application that is deployed to a Mckoi
Web Platform installation that provides a console based user interface, via
HTML5/JavaScript, similar to a CLI you would find in an operating system.
The CLI is used as a web application development platform and for
administration of the network. The CLI runs a modified version of Node
(https://nodejs.org/).
