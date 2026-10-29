---
id: adoption-view
title: Adoption View
sidebar_position: 1
---

import Banner from './banner.mdx'

![Connector kit banner](@site/static/img/kits/connector/logo.svg)

The Connector KIT describes the infrastructure for sovereign cross-enterprise data exchange in a dataspace.

## Important terms

- Data Provider: a company that offers data to others under defined usage policies.
- Data Consumer: a company that negotiates a contract and then consumes the offered data.

## Policies

Access and usage policies are expressed in ODRL. A provider attaches a policy to every asset. The consumer
negotiates a contract agreement before any data transfer starts.

:::info
Policies are evaluated by the control plane of the connector.
:::

## Business Partner Number

Every participant is identified by a Business Partner Number for legal entities (BPNL). The BPNL is part of the
membership credential and is checked during the contract negotiation.
