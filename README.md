# Generic HTTP backend for Terraform

## Description

This project contains a simple and generic HTTP backend for Terraform. The backend can be configured to use different storage adapter: either a database, or an ElasticSearch cluster. The backend supports state locking.

This project uses Quarkus, the Supersonic Subatomic Java Framework.
If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Compatible storages

The backend can store the Terraform state using one of the following product:
- MariaDB _(using Hibernate ORM)_
- MySQL _(using Hibernate ORM)_
- PostgreSQL _(using Hibernate ORM)_
- Microsoft SQL Server _(using Hibernate ORM)_
- ElasticSearch _(using ES high-level REST client)_

## How to...

### ...configure the backend

The configuration of the backend can be found in `src/main/resources/application.properties`. The properties contained in this file can be overridden from the command line or from the environment variables. You can find more about that in the [Quarkus configuration reference guide](https://quarkus.io/guides/config-reference).

The most important property is `application.storage.adapter`. This property determines which storage adapter the backend will use to store the Terraform state. 

To store in a database, use the value `database` and configure the database connection by using the `quarkus.datasource.*` properties. Note that `database` is the default value for the backend.

```properties
application.storage.adapter=database

quarkus.datasource.db-kind=mariadb
quarkus.datasource.username=root
quarkus.datasource.password=root
quarkus.datasource.jdbc.url=jdbc:mariadb://localhost:3306/terraform
```

To store in an ElasticSearch cluster, use the value `elastic` and configure the ES client by using the `quarkus.elasticsearch.*` properties.

```properties
application.storage.adapter=elastic

quarkus.elasticsearch.hosts=node1:9200,node2:9200,node3:9200
quarkus.elasticsearch.protocol=https
quarkus.elasticsearch.username=user
quarkus.elasticsearch.password=password
```

### ...configure Terraform

To use this backend, the `terraform` block in the root module must be updated to use the `http` backend.

There are 2 variants for the communications between Terraform and the backend:
1. using the custom HTTP methods `LOCK` and `UNLOCK`

```hcl
terraform {
  required_version = "~> 0.14.0"

  backend "http" {
    address = "http://my-http-backend:8080/tf-state/my-project"
    lock_address = "http://my-http-backend:8080/tf-state/my-project"
    unlock_address = "http://my-http-backend:8080/tf-state/my-project"
  }
  ...
}
```

2. using only the standard HTTP methods
 
```hcl
terraform {
  required_version = "~> 0.14.0"

  backend "http" {
    address = "http://my-http-backend:8080/tf-state/my-project"
    lock_address = "http://my-http-backend:8080/tf-state/my-project/lock"
    lock_method = "POST"
    unlock_address = "http://my-http-backend:8080/tf-state/my-project/unlock"
    unlock_method = "POST"
  }
  ...
}
```

If using LOCK and LOCK http methods, all URLs are built the following way:

```
<protocol>://<backend>/tf-state/<project>
```

If using only standard HTTP methods, the URLs are built the following way:

```
# address
<protocol>://<backend>/tf-state/<project>
# lock_address
<protocol>://<backend>/tf-state/<project>/lock
# unlock_address
<protocol>://<backend>/tf-state/<project>/unlock
```

- _protocol_: `http` or `https`
- _backend_: the IP or the domain name to point to the backend (with the port if necessary)
- _project_: the name of the Terraform project to manage. It is recommended in a multi-environment project to also have the environment as part of the project name (for instance `my-project--prod` instead of just `my-project`).

Therefore, in the Terraform configuration examples above (point 1 and 2):
- the protocol is `http`
- the backend is accessible at `my-http-backend` with the port `8080`
- the project name is `my-project` (no multi-environment as it is not specified)

_**REMARK**_

The format of the project name (with or without the environment) is not preset. You can choose the format that suits your need the most. For example, at [Sedona](http://sedona.fr), we use the following format `<env>--<project name>`.

