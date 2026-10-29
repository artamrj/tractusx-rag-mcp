# MCP server

The app serves the [Model Context Protocol](https://modelcontextprotocol.io) over Streamable HTTP at `/mcp`, so AI
agents can use the Tractus-X docs as a tool.

<img src="assets/demo-mcp.svg" alt="An AI agent using the MCP tools" width="100%">

## Tools

All tools are read-only.

| Tool | Arguments | Returns |
|---|---|---|
| `search_tractusx_docs` | `query`, `kit?`, `top_k?` | Ranked excerpts with page URLs; no LLM cost on the server |
| `ask_tractusx_docs` | `question`, `kit?` | Grounded answer with numbered source links |
| `list_tractusx_kits` | – | Indexed KIT ids (usable as `kit`) with page counts |

## Connect a client

**Claude Code**

```bash
claude mcp add --transport http tractusx-docs http://localhost:8080/mcp
```

**opencode** (`~/.config/opencode/opencode.json` or `.jsonc`)

```json
{
  "mcp": {
    "tractusx-docs": {
      "type": "remote",
      "url": "http://localhost:8080/mcp",
      "oauth": false,
      "timeout": 120000
    }
  }
}
```

**MCP Inspector**

```bash
npx @modelcontextprotocol/inspector
```

Choose *Streamable HTTP* and `http://localhost:8080/mcp`.

If `RAG_API_KEY` is set, send the header `X-Api-Key` (Claude Code: `--header "X-Api-Key: <key>"`, opencode:
`"headers": {"X-Api-Key": "<key>"}`).

## Check that the tools are used

Ask something only the docs answer, e.g. *"In the Industry Core KIT, how was the Digital Twin Registry load-tested and
what throughput was reached?"* (JMeter, 20 threads, 30–35 transactions/s). Then:

```bash
curl -s localhost:8080/actuator/prometheus | grep rag_mcp_tool_calls_total
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Agent says the tools are not available after a container rebuild | MCP sessions live in server memory. Restart the client (for opencode also its background service, `opencode serve`) |
| Agent answers from general knowledge | Name the tool: *"Use ask_tractusx_docs: …"*; clients with a tool catalog may need *"search your tool catalog for tractusx-docs first"* |
| `ask_tractusx_docs` returns an error | No `ANTHROPIC_API_KEY` in the app; `search_tractusx_docs` still works |
