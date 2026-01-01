## Mass Assignment Vulnerability

### Definition

**Mass assignment** is a vulnerability that occurs when an API automatically binds client-supplied data to server-side objects without restricting which fields the client is allowed to control.

The issue exists even when certain fields are hidden from API documentation tools.

---

## A scenario

An API exposes an endpoint for creating an object.
The request body includes only user-controlled fields, while internal fields are hidden from documentation.

For example, An API exposes an endpoint for creating a recipe.
The documented request body contains the following fields 

```json
{
  "name": "string",
  "description": "string",
  "imageUrl": "string"
}
```

Internally, the object also contains a field such as:

* `status`

Although this field is **not shown** in Swagger or OpenAPI, the backend still accepts it if it appears in the JSON payload.

---

## Why this is vulnerable

Documentation tools affect **visibility**, not **behavior**.

If the server:

* Deserializes JSON into an object
* Binds all recognized fields automatically

Then a client can manually send:

```json
{
  "name": "Cake",
  "description": "Nice",
  "imageUrl": "x",
  "status": "PUBLISHED"
}
```

If the server accepts this value, the recipe is published authomatically without review. the client has gained control over a server-managed property.

This is a **mass assignment vulnerability**.

---

## Key misconception

❌ “If it’s hidden in Swagger, clients can’t send it”

**Reality:**
Attackers do not use Swagger.
They send raw HTTP requests.

---

## Impact

Mass assignment can allow:

* Workflow bypass
* Privilege escalation
* Unauthorized state changes
* Business rule violations

The vulnerability is often silent and hard to detect.

---

## Secure design rule

> Clients must only be able to set fields they are explicitly authorized to control.

---

## Recommended solution (best practice)

**Request DTO**

* Contains only client-controlled fields

**Response / domain model**

* Contains server-controlled fields

Server logic assigns sensitive fields explicitly:

```java
status = NEW;
```

This provides a **type-level guarantee**.

---

## Defensive reinforcement

* Reject unknown JSON fields at deserialization time
* Never bind request bodies directly to domain entities
* Treat documentation as informational, not protective

---

## What does not prevent mass assignment

* Swagger field hiding
* Frontend validation
* Naming conventions
* Developer discipline alone

---

## Security takeaway

Mass assignment is not a validation problem.
It is an **authority problem**.

If a client can send a value and the server binds it, the client controls it.

