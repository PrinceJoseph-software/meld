import { test } from 'node:test';
import assert from 'node:assert/strict';
import { computeResults, instantRunoff } from '../src/shared/tally.ts';

const opts = ['A', 'B', 'C', 'D'].map((id, i) => ({ id, sortOrder: i }));

test('majority in round one wins immediately', () => {
  const r = instantRunoff(opts, [['A'], ['A'], ['B']]);
  assert.equal(r.winnerId, 'A');
  assert.equal(r.rounds.length, 1);
});

test('eliminated choices transfer to next preferences', () => {
  // first round A2 B2 C1 -> C eliminated, its ballot goes to B -> B wins 3/5
  const r = instantRunoff(opts, [['A'], ['A'], ['B'], ['B'], ['C', 'B']]);
  assert.equal(r.winnerId, 'B');
  assert.equal(r.rounds[0].eliminated !== null, true);
  assert.deepEqual(r.rounds[r.rounds.length - 1].counts.B, 3);
});

test('exhausted ballots drop out of the majority threshold', () => {
  const r = instantRunoff(opts, [['A'], ['A'], ['B'], ['C'], ['D']]);
  assert.equal(r.winnerId, 'A');
});

test('a dead heat is resolved deterministically and flagged', () => {
  const r = instantRunoff(opts, [['A', 'B'], ['B', 'A']]);
  assert.equal(r.tie, true);
  assert.equal(r.winnerId, 'A'); // equal Borda -> earlier-listed option
});

test('plurality results for normal rooms', () => {
  const res = computeResults(false, opts, [['A'], ['B'], ['B']]);
  assert.equal(res.leaderId, 'B');
  assert.equal(res.total, 3);
});

test('ignores unknown and duplicate ids', () => {
  const r = instantRunoff(opts, [['Z', 'A', 'A'], ['A']]);
  assert.equal(r.winnerId, 'A');
});
