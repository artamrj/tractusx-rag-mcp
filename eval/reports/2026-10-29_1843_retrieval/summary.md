# Evaluation 2026-10-29

- Test set: `eval/testset.yaml`, 60 questions {cross-kit=8, factual=32, procedural=14, unanswerable=6}, 5 in German
- **60 items are not marked `reviewed: true` yet**
- Retrieval: embeddings `text-embedding-3-small` (openai), chunk table `chunks_openai_te3_small`, RRF k=60, keyword weight 0.50
- Answer model and judge: `claude-opus-5-5` (not used: retrieval-only run)
- Retrieval metrics on answerable items; a hit means a chunk from an expected page was retrieved

| Config | Hit@1 | Hit@3 | Hit@k | Recall@k | MRR |
|---|---|---|---|---|---|
| vector-k6 | 80% | 93% | 96% | 82% | 0.87 |
| hybrid-k6 | 74% | 93% | 98% | 84% | 0.84 |
| vector-k8 | 80% | 93% | 96% | 84% | 0.87 |
| hybrid-k8 | 74% | 93% | 100% | 87% | 0.84 |
| vector-k10 | 80% | 93% | 98% | 86% | 0.87 |
| hybrid-k10 | 74% | 93% | 100% | 89% | 0.84 |

Correctness = share of reference key points covered; Faithfulness = share of answer claims supported by the retrieved excerpts; both judged by the LLM with a fixed rubric.
