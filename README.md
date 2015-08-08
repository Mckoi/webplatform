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

This repository is organised in a series of sub-projects that have
dependence on each other.

## Projects

### MWPCore: MWP Core System

The core application server system code that implements a distributed network
for managing in-memory processes, and operates an embedded version of Jetty 8.

This is a server application that is installed on Unix or Windows servers that
allows the machine to be a HTTP/HTTPS host (via Jetty) and/or a process host for
tasks executed within the Mckoi Web Platform network. The project also
includes administration and installation tools.

### MWPMain: MWP Application Main

A relatively simple bootstrap application that starts up and configures Java
applications within the Mckoi Web Platform framework as specified by the
network administration.

### MWPClient: Client Side Development Tools

Client command-line tools for managing projects on a remote account in a
Mckoi Web Platform.

Some development tasks, such as synchronizing files on a local computer with
a remote MWP account, can not be implementing in HTML5/JavaScript on a web
browser. For such tasks the MWPClient project provides tools for developers
to overcome these limitations.

### MWPConsoleWebApp: MWP Console UI (Webapp)

A .war web application that runs only on the Mckoi Web Platform that implements
a HTML5/JavaScript based console user interface.

This web applications includes a developers toolkit for writing, deploying and
administrating web applications inside the Mckoi Web Platform. Only a web
browser is necessary to log in and use.

The toolkit is based around a console interface similar to CLIs found in many
operating systems. It supports familiar file system commands such as 'ls',
'rm', 'mkdir', etc. It also provides a text editor (http://codemirror.net/),
log viewer and database viewer.

### MckoiJasper2: Modified version of Apache Tomcat JSP Engine (Jasper)

A slightly modified version of Jasper that allows for the Mckoi Web Platform
to target JSP source and compiled files in a none native file system (so we can
support JSP files stored within a MckoiDDB file system).

### MWPTestSuiteApp: Unit tests (Webapp)

A .war web application that runs only on the Mckoi Web Platform that runs a
series of unit tests on features of the entire platform.
