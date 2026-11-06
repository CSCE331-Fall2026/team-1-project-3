
## 2026-11-04: Database
- Reset the `receipt` and `receipt_item` ID sequences to the current max IDs
  (`setval`). The seed data was inserted with explicit IDs, so the sequences
  were at 2 and 10, and every checkout failed with a duplicate key. Anyone
  reloading seed data must run the same `setval` afterward.
