
## 2026-11-04: Database
- Reset the `receipt` and `receipt_item` ID sequences to the current max IDs
  (`setval`). The seed data was inserted with explicit IDs, so the sequences
  were at 2 and 10, and every checkout failed with a duplicate key. The
  generator now ends `Script.sql` and `Reset.sql` with a re-runnable sync of
  every identity/serial sequence, so reseeding needs no manual `setval`.
