# Evaluation 2026-10-29

- Test set: `eval/testset.yaml`, 60 questions {cross-kit=8, factual=32, procedural=14, unanswerable=6}, 5 in German
- **60 items are not marked `reviewed: true` yet**
- Retrieval: embeddings `all-minilm-l6-v2-q` (local), chunk table `chunks_minilm_l6`, RRF k=60, keyword weight 0.50
- Answer model and judge: `claude-opus-5-5` (not used: retrieval-only run)
- Retrieval metrics on answerable items; a hit means a chunk from an expected page was retrieved

| Config | Hit@1 | Hit@3 | Hit@k | Recall@k | MRR |
|---|---|---|---|---|---|
| vector-k6 | 61% | 76% | 85% | 72% | 0.70 |
| hybrid-k6 | 57% | 80% | 83% | 69% | 0.69 |
| vector-k10 | 61% | 76% | 93% | 78% | 0.71 |
| hybrid-k10 | 57% | 80% | 93% | 79% | 0.70 |

Correctness = share of reference key points covered; Faithfulness = share of answer claims supported by the retrieved excerpts; both judged by the LLM with a fixed rubric.
