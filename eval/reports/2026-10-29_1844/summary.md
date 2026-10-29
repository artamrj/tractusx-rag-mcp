# Evaluation 2026-10-29

- Test set: `eval/testset.yaml`, 60 questions {cross-kit=8, factual=32, procedural=14, unanswerable=6}, 5 in German
- **60 items are not marked `reviewed: true` yet**
- Retrieval: embeddings `text-embedding-3-small` (openai), chunk table `chunks_openai_te3_small`, RRF k=60, keyword weight 0.50
- Answer model and judge: `claude-opus-5-5`
- Retrieval metrics on answerable items; a hit means a chunk from an expected page was retrieved

| Config | Hit@1 | Hit@3 | Hit@k | Recall@k | MRR | Correctness | Fully correct | Faithfulness | Citation precision | Abstains when docs lack answer | False abstentions | p50 / p95 latency | Cost |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| hybrid-k8 | 74% | 93% | 100% | 87% | 0.84 | 80% | 61% | 98% | 99% | 100% | 6% | 10.2 s / 15.3 s | $4.83 |

Correctness = share of reference key points covered; Faithfulness = share of answer claims supported by the retrieved excerpts; both judged by the LLM with a fixed rubric.
