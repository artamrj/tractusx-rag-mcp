---
id: software-development-view
title: Software Development View
---

## Digital Twin Registry

The Digital Twin Registry (DTR) stores shell descriptors following the Asset Administration Shell (AAS) API.
A data provider registers one shell descriptor per digital twin.

## Registering a digital twin

To register a twin, send a POST request with the shell descriptor to the shell-descriptors endpoint:

```bash
curl -X POST https://dtr.example.com/api/v3/shell-descriptors -H 'Content-Type: application/json' -d @shell.json
```

Each submodel descriptor points to a submodel endpoint that is reachable through the connector data plane.

## Lookup

Consumers find twins with the lookup API by searching for specific asset ids such as the manufacturerPartId.
