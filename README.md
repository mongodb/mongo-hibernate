# A MongoDB Dialect for the Hibernate ORM

This project aims to provide a library to seamlessly integrate MongoDB with Hibernate ORM. Hibernate _ORM_ is a powerful **O**bject-**r**elational **m**apping tool. Due to the SQL and JDBC standards, Hibernate ORM could centralize each SQL vendor's idiosyncrasies in the so-called _Hibernate Dialect_. But this project will include a NoSQL member in the Hibernate's Dialect family.

## Overview

MongoDB speaks _MQL_ (**M**ongoDB **Q**uery **L**anguage in JSON format) instead of SQL, and lacks a full-fledged JDBC Driver currently. However, MongoDB does support db transaction and table joins simulation. This project will create a NoSQL Hibernate Dialect by bridging the gaps, particularly:

- create a JDBC adapter using [MongoDB Java Driver](https://www.mongodb.com/docs/drivers/java-drivers/)
- translate Hibernate's internal SQL AST into MQL

<img src="hibernate-dialects.png" alt="MongoDB Dialect" width="800"/>

## Development

Java 17 is the JDK version for development.

Initially Hibernate ORM v6.6 is the dependency version.

### Build from source

### Test

## References

- [An Introduction to Hibernate 6](https://docs.jboss.org/hibernate/orm/6.6/introduction/html_single/Hibernate_Introduction.html)
- [A Guide to Hibernate Query Language](https://docs.jboss.org/hibernate/orm/6.6/querylanguage/html_single/Hibernate_Query_Language.html)
- [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html)













