#!/usr/bin/env python3
"""Generates deploy/grafana/dashboards/rag-tractusx.json (run after changing metrics or panels)."""
import json
import pathlib

DS = {"type": "prometheus", "uid": "${datasource}"}
CAT = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#6250d6", "#e34948"]
# Outcome colours: status palette for state, neutral grey for "docs don't cover it" (expected, not a warning).
# Errors are not stacked with the outcomes (red next to green fails for deuteranopia); they have their own panels.
OUTCOME = {"answered": "#0ca30c", "no_answer": "#6b6a65", "refused": "#ec835a"}
WARN, CRITICAL = "#fab219", "#d03b3b"
APP = 'application="rag-tractusx"'
# Estimated USD per million tokens (Claude Opus 5.5); adjust the dashboard variables for other models.
panels = []
_id = [0]


def nid():
    _id[0] += 1
    return _id[0]


def target(expr, legend="", ref="A"):
    return {"datasource": DS, "expr": expr, "legendFormat": legend, "refId": ref, "range": True}


def stat(title, expr, x, unit="short", decimals=None, desc="", thresholds=None):
    p = {
        "id": nid(), "type": "stat", "title": title, "description": desc, "datasource": DS,
        "gridPos": {"h": 4, "w": 4, "x": x, "y": 0},
        "targets": [target(expr)],
        "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                    "colorMode": "none" if not thresholds else "background", "graphMode": "none",
                    "textMode": "value", "justifyMode": "center"},
        "fieldConfig": {"defaults": {"unit": unit, "color": {"mode": "thresholds"},
                                     "thresholds": thresholds or {"mode": "absolute", "steps": [{"color": "text", "value": None}]}},
                        "overrides": []},
    }
    if decimals is not None:
        p["fieldConfig"]["defaults"]["decimals"] = decimals
    panels.append(p)


def series(title, targets, x, y, w=12, h=8, unit="short", desc="", colors=None, stack=False, minzero=True):
    overrides = []
    for name, color in (colors or {}).items():
        overrides.append({"matcher": {"id": "byName", "options": name},
                          "properties": [{"id": "color", "value": {"mode": "fixed", "fixedColor": color}}]})
    defaults = {
        "unit": unit, "color": {"mode": "palette-classic"},
        "custom": {"drawStyle": "line", "lineWidth": 2, "fillOpacity": 10 if stack else 0, "showPoints": "never",
                   "spanNulls": True, "stacking": {"mode": "normal" if stack else "none", "group": "A"},
                   "axisSoftMin": 0 if minzero else None, "gradientMode": "none"},
    }
    panels.append({
        "id": nid(), "type": "timeseries", "title": title, "description": desc, "datasource": DS,
        "gridPos": {"h": h, "w": w, "x": x, "y": y},
        "targets": targets,
        "options": {"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
                    "tooltip": {"mode": "multi", "sort": "desc"}},
        "fieldConfig": {"defaults": defaults, "overrides": overrides},
    })


ask = f'rag_ask_duration_seconds_count{{{APP}}}'
stat("Questions (24h)", f'sum(increase({ask}[24h]))', 0, decimals=0)
stat("Answered (1h)", f'sum(rate(rag_ask_duration_seconds_count{{{APP},outcome="answered"}}[1h])) / sum(rate({ask}[1h]))',
     4, unit="percentunit", decimals=1, desc="Share of questions answered with at least one citation")
stat("p95 answer time (5m)", f'histogram_quantile(0.95, sum by (le) (rate(rag_ask_duration_seconds_bucket{{{APP}}}[5m])))',
     8, unit="s", decimals=1)
stat("Indexed chunks", f'max(rag_index_chunks{{{APP}}})', 12, decimals=0)
stat("Since last ingestion", f'time() - max(rag_ingestion_last_success_timestamp_seconds{{{APP}}} > 0)', 16, unit="s",
     desc="Time since the last successful ingestion run in any pod",
     thresholds={"mode": "absolute", "steps": [{"color": "transparent", "value": None},
                                               {"color": WARN, "value": 8 * 86400},
                                               {"color": CRITICAL, "value": 15 * 86400}]})
stat("LLM cost est. (24h)",
     f'sum(increase(rag_llm_tokens_total{{{APP},type="input"}}[24h])) * $input_price / 1e6'
     f' + sum(increase(rag_llm_tokens_total{{{APP},type="output"}}[24h])) * $output_price / 1e6',
     20, unit="currencyUSD", decimals=2, desc="Token counts × the price variables at the top of the dashboard")

series("Questions per minute by outcome",
       [target(f'sum by (outcome) (rate(rag_ask_duration_seconds_count{{{APP},outcome!="error"}}[$__rate_interval])) * 60', "{{outcome}}")],
       0, 4, unit="short", colors=OUTCOME, stack=True,
       desc="answered = cited the docs; no_answer = docs did not cover it; refused = model declined. Failures are in the error panels.")
series("Answer time",
       [target(f'histogram_quantile(0.50, sum by (le) (rate(rag_ask_duration_seconds_bucket{{{APP}}}[$__rate_interval])))', "p50", "A"),
        target(f'histogram_quantile(0.95, sum by (le) (rate(rag_ask_duration_seconds_bucket{{{APP}}}[$__rate_interval])))', "p95", "B"),
        target(f'histogram_quantile(0.99, sum by (le) (rate(rag_ask_duration_seconds_bucket{{{APP}}}[$__rate_interval])))', "p99", "C")],
       12, 4, unit="s", colors={"p50": CAT[0], "p95": CAT[1], "p99": CAT[2]})
series("LLM tokens per minute",
       [target(f'sum by (type) (rate(rag_llm_tokens_total{{{APP}}}[$__rate_interval])) * 60', "{{type}}")],
       0, 12, colors={"input": CAT[0], "output": CAT[1], "cache_read": CAT[2]})
series("Retrieval time (p95)",
       [target(f'histogram_quantile(0.95, sum by (le, mode) (rate(rag_retrieval_duration_seconds_bucket{{{APP}}}[$__rate_interval])))', "p95 {{mode}}")],
       12, 12, unit="s", colors={"p95 hybrid": CAT[0], "p95 vector": CAT[1]})
series("Best-match similarity (avg)",
       [target(f'sum(rate(rag_retrieval_top_score_sum{{{APP}}}[$__rate_interval])) / sum(rate(rag_retrieval_top_score_count{{{APP}}}[$__rate_interval]))', "top score")],
       0, 20, unit="percentunit", colors={"top score": CAT[0]},
       desc="Average cosine similarity of the best chunk; a drop means questions drift away from the indexed docs")
series("Failed questions and upstream errors per minute",
       [target(f'sum(rate(rag_ask_duration_seconds_count{{{APP},outcome="error"}}[$__rate_interval])) * 60', "failed questions", "A"),
        target(f'sum(rate(rag_llm_errors_total{{{APP}}}[$__rate_interval])) * 60', "LLM errors", "B")],
       12, 20, colors={"failed questions": CRITICAL, "LLM errors": CAT[0]})
series("MCP tool calls per minute",
       [target(f'sum by (tool) (rate(rag_mcp_tool_calls_total{{{APP}}}[$__rate_interval])) * 60', "{{tool}}")],
       0, 28, colors={"search_tractusx_docs": CAT[0], "ask_tractusx_docs": CAT[1], "list_tractusx_kits": CAT[2]})
series("HTTP 5xx per minute",
       [target(f'sum(rate(http_server_requests_seconds_count{{{APP},status=~"5.."}}[$__rate_interval])) * 60', "5xx")],
       12, 28, colors={"5xx": CRITICAL})
series("JVM heap used",
       [target(f'sum by (instance) (jvm_memory_used_bytes{{{APP},area="heap"}})', "{{instance}}")],
       0, 36, unit="bytes")
series("Ingestion file changes (per run window)",
       [target(f'sum by (change) (increase(rag_ingestion_files_total{{{APP}}}[1h]))', "{{change}}")],
       12, 36, colors={"added": CAT[0], "updated": CAT[1], "deleted": CAT[2], "failed": CRITICAL})

dashboard = {
    "uid": "rag-tractusx", "title": "RAG Tractus-X Assistant", "tags": ["rag", "tractus-x", "llm"],
    "timezone": "browser", "schemaVersion": 39, "version": 1, "refresh": "30s",
    "time": {"from": "now-6h", "to": "now"},
    "templating": {"list": [
        {"name": "datasource", "type": "datasource", "query": "prometheus", "label": "Data source",
         "current": {}, "hide": 0},
        {"name": "input_price", "type": "constant", "query": "4", "label": "USD per 1M input tokens", "hide": 2},
        {"name": "output_price", "type": "constant", "query": "20", "label": "USD per 1M output tokens", "hide": 2},
    ]},
    "panels": panels,
    "annotations": {"list": []},
}
out = pathlib.Path(__file__).resolve().parent.parent / "deploy/grafana/dashboards/rag-tractusx.json"
out.write_text(json.dumps(dashboard, indent=2) + "\n")
print(f"wrote {out} ({len(panels)} panels)")
