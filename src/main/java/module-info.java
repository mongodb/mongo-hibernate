/** The MongoDB Extension for Hibernate ORM module. */
module com.mongodb.hibernate {
    requires java.naming;
    requires java.sql;
    requires jakarta.persistence;
    requires transitive org.hibernate.orm.core;
    requires org.mongodb.bson;
    requires transitive org.mongodb.driver.core;
    requires org.mongodb.driver.sync.client;
    requires org.jspecify;

    exports com.mongodb.hibernate.annotations;
    exports com.mongodb.hibernate.cfg;
    exports com.mongodb.hibernate.service.spi;
}
