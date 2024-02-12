# Payment Gateway Mockup

## Purpose

This service is meant to demo a payment gateway service.  It serves two main functions:

- it proxies calls to a payment processor service to actually retrieve a decision on a card transaction
- it writes information to a database to add persistence to the process

It is meant to be run in conjunction with the Payment Processor mockup service in this same repo.

Since this app is stateless, multiple instances of it can be run with a load balancer in front of it.

## Installation

### Prerequisites

- **Java SDK version 17** installed, on the server where this code is to be run (the project was written with Java 17 in mind, although it will probably work fine with versions higher than 17)<br/><br/>You can check your java version by running `java -version`<br/><br/>
- **Maven**, on the server where you have the code<br/><br/>You can verify Maven is installed by running `mvn -version`<br/><br/>
- a running instance of **CockroachDB 23.2.0+** (we need at least 23.2 because we're using the Field-level encryption functions)<br/><br/>You can check your Cockroach version by running `cockroach --version`<br/><br/>

To install and run this code, you need to do the following

1. Pull the code locally from the github repository.<br/><br/>

2. Set the JAVA_HOME environment variable pointing to your Java 17 SDK; for example

       export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

3. Run `mvn package -Dmaven.test.skip` from the project directory to package this executable as a JAR file<br/><br/>After this command finishes, you should see a jar file in the target directory:

       $ ls target/*.jar
       target/PaymentGatewayMockup-0.0.1-SNAPSHOT.jar

   Note: If you leave off the `-Dmaven.test.skip` argument, some tests will run which will try to connect to the database.  If you want to run these, you can; but you need to have a valid DB connection string in the src/main/resources/application.properties file.<br/><br/>

4. In the CockroachDB cluster, there needs be a database called "payments" (because we're passing "payments" as the DB name in the JDBC URL).  The code will create the necessary tables in this database.<br/><br/>If you need to create this database, here's an example of how to do this:

       $ cockroach sql --url 'postgresql://localhost:26257/defaultdb?sslmode=verify-full&sslcert=/Users/jimhatcher/local_certs/client.root.crt&sslkey=/Users/jimhatcher/local_certs/client.root.key&sslrootcert=/Users/jimhatcher/local_certs/ca.crt'
       #
       # Welcome to the CockroachDB SQL shell.
       # All statements must be terminated by a semicolon.
       # To exit, type: \q.
       #
       # Server version: CockroachDB CCL v23.2.0 (aarch64-apple-darwin21.2, built 2024/01/16 20:09:40, go1.21.5 X:nocoverageredesign) (same version as client)
       # Cluster ID: 1e1e1946-3b0d-4679-a458-0a54c3f1abe7
       # Organization: hatcherdev
       #
       # Enter \? for a brief introduction.
       #
       root@localhost:26257/defaultdb> create database if not exists payments;                                                                                   
       CREATE DATABASE

       Time: 6ms total (execution 6ms / network 0ms)

5. Configure the settings, including the address of the payments processor service and the connection to the CockroachDB database.  This is configured in the code in the application.properties file.  You can override the properties that were packaged up with the JAR file by specifying the location of an external properties file:

       java -jar <jar file> --spring.config.location=gateway.properties

   The file should look like this (see: src/main/resources/application.properties):

       server.port=8081

       ## default connection pool
       spring.datasource.hikari.connection-timeout=20000
       spring.datasource.hikari.maximum-pool-size=5
       spring.datasource.hikari.max-lifetime=300000
       spring.datasource.hikari.keepalive-time=150000

       ## PostgreSQL
       spring.datasource.url=jdbc:postgresql://localhost:26257/payments?sslmode=verify-full&sslcert=/Users/jimhatcher/local_certs/client.root.crt.der&sslkey=/Users/jimhatcher/local_certs/client.root.key.der&sslrootcert=/Users/jimhatcher/local_certs/ca.crt.der
       spring.datasource.username=root
       spring.datasource.password=
       # this next property keeps me from getting a createClob() warning at startup
       spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true
       #tell JPA to not auto-generator our schema objects
       spring.jpa.hibernate.ddl-auto=none

       payment_demo.processor_url=http://localhost:8082/transactions

    Note that the certs specified in the JDBC url in this config file uses DER-formatted certs and keys.  The default format for certs and keys created by cockroach are not DER-formatted, but Java likes DER-formatted certs and keys.  To convert the certs provided by Cockroach to DER, run the following:

       openssl x509 -outform der -in ca.crt -out ca.crt.der
       openssl x509 -outform der -in client.root.crt -out client.root.crt.der
       openssl pkcs8 -topk8 -inform PEM -outform DER -in client.root.key -out client.root.key.der -nocrypt

6. Run the service by executing a command like the following.  It is a good idea to specify the max heap memory for this process.  In the example below, we're specifying a max heap size of 2GB with `-Xmx2g`. 

       java -Xmx2g -jar target/PaymentGatewayMockup-0.0.1-SNAPSHOT.jar --spring.config.location=gateway.properties